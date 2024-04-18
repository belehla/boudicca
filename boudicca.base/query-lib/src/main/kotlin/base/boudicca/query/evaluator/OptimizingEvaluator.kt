package base.boudicca.query.evaluator

import base.boudicca.model.Entry
import base.boudicca.query.*
import base.boudicca.query.evaluator.util.EvaluatorUtil
import base.boudicca.query.evaluator.util.FullTextIndex
import base.boudicca.query.evaluator.util.SimpleIndex
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class OptimizingEvaluator(rawEntries: Collection<Entry>) : Evaluator {

    private val dateCache = ConcurrentHashMap<String, OffsetDateTime>()
    private val entries = Utils.order(rawEntries, dateCache)
    private val fullTextIndexCache = mutableMapOf<String, FullTextIndex>()
    private val simpleIndexCache = mutableMapOf<String, MutableMap<String, SimpleIndex<*>>>()
    private val allFields = getAllFields(entries)

    init {
        // init contains searches
        for (field in allFields) {
            getOrCreateFullTextIndex(field)
        }
    }

    override fun evaluate(expression: Expression, page: Page): QueryResult {
        val resultSet = evaluateExpression(expression)
        val resultSize = resultSet.cardinality()
        val orderedResult = if (resultSize > entries.size / 3) {
            // roughly, if the resultset is bigger then a third of the total entries,
            // it is faster to iterate over all entries then to sort the resultset
            entries.filterIndexed { i, _ -> resultSet.get(i) }
        } else {
            Utils.order(resultSet.stream().mapToObj{ entries[it] }.toList(), dateCache)
        }
        return QueryResult(
            orderedResult
                .drop(page.offset)
                .take(page.size)
                .toList(),
            resultSize
        )
    }

    private fun evaluateExpression(expression: Expression): BitSet {
        when (expression) {
            is EqualsExpression -> {
                return equalsExpression(expression)
            }

            is ContainsExpression -> {
                return containsExpression(expression)
            }

            is NotExpression -> {
                return notExpression(expression)
            }

            is AndExpression -> {
                return andExpression(expression)
            }

            is OrExpression -> {
                return orExpression(expression)
            }

            is BeforeExpression -> {
                return beforeExpression(expression)
            }

            is AfterExpression -> {
                return afterExpression(expression)
            }

            is DurationLongerExpression -> {
                return durationLongerExpression(expression)
            }

            is DurationShorterExpression -> {
                return durationShorterExpression(expression)
            }

            is HasFieldExpression -> {
                return hasFieldExpression(expression)
            }

            else -> {
                throw QueryException("unknown expression kind $expression")
            }
        }
    }

    private fun hasFieldExpression(expression: HasFieldExpression): BitSet {
        val resultSet = BitSet()
        entries.forEachIndexed { i, event ->
            if(event.containsKey(expression.getFieldName()) && event[expression.getFieldName()]!!.isNotEmpty()){
                resultSet.set(i)
            }
        }
        return resultSet
    }

    private fun notExpression(expression: NotExpression): BitSet {
        val subEvents = evaluateExpression(expression.getChild())
        subEvents.flip(0, entries.size) //TODO this modifies the set, is that ok? can cause problems with caching
        return subEvents
    }

    private fun orExpression(expression: OrExpression): BitSet {
        val leftSubEvents = evaluateExpression(expression.getLeftChild())
        val rightSubEvents = evaluateExpression(expression.getRightChild())
        leftSubEvents.or(rightSubEvents)
        return leftSubEvents
    }

    private fun andExpression(expression: AndExpression): BitSet {
        val leftSubEvents = evaluateExpression(expression.getLeftChild())
        val rightSubEvents = evaluateExpression(expression.getRightChild())
        leftSubEvents.and(rightSubEvents)
        return leftSubEvents
    }

    private fun equalsExpression(expression: EqualsExpression): BitSet {
        val lowerCase = expression.getText().lowercase()
        return starFieldSearch(expression.getFieldName()) { field ->
            val index = getOrCreateSimpleIndex("equals", field) {
                SimpleIndex<String?>(entries.map { it[field]?.lowercase() }, Comparator.naturalOrder<String?>())
            }
            index.search { it?.compareTo(lowerCase) ?: -1 }
        }
    }

    private fun containsExpression(expression: ContainsExpression): BitSet {
        return starFieldSearch(expression.getFieldName()) { field ->
            val index = getOrCreateFullTextIndex(field)
            index.containsSearch(expression.getText())
        }
    }

    private fun beforeExpression(expression: BeforeExpression): BitSet {
        val index = getOrCreateLocalDateIndex(expression.getFieldName())
        return index.search {
            if (it != null) {
                if (it.isEqual(expression.getDate()) || it.isBefore(expression.getDate())) {
                    0
                } else {
                    1
                }
            } else {
                -1
            }
        }
    }

    private fun afterExpression(expression: AfterExpression): BitSet {
        val index = getOrCreateLocalDateIndex(expression.getFieldName())
        return index.search {
            if (it != null) {
                if (it.isEqual(expression.getDate()) || it.isAfter(expression.getDate())) {
                    0
                } else {
                    -1
                }
            } else {
                -1
            }
        }
    }

    private fun durationLongerExpression(expression: DurationLongerExpression): BitSet {
        val index = getDurationIndex(expression)
        val duration = expression.getDuration().toDouble()
        return index.search {
            if (it != null) {
                if (it >= duration) {
                    0
                } else {
                    -1
                }
            } else {
                -1
            }
        }
    }

    private fun durationShorterExpression(expression: DurationShorterExpression): BitSet {
        val index = getDurationIndex(expression)
        val duration = expression.getDuration().toDouble()
        return index.search {
            if (it != null) {
                if (it <= duration) {
                    0
                } else {
                    1
                }
            } else {
                -1
            }
        }
    }

    private fun getDurationIndex(expression: AbstractDurationExpression): SimpleIndex<Double?> {
        val index =
            getOrCreateSimpleIndex("duration", expression.getStartDateField() + "&" + expression.getEndDateField()) {
                SimpleIndex<Double?>(entries.map {
                    EvaluatorUtil.getDuration(
                        expression.getStartDateField(),
                        expression.getEndDateField(),
                        it, dateCache
                    )
                }, Comparator.naturalOrder<Double?>())
            }
        return index
    }

    private fun getOrCreateLocalDateIndex(fieldName: String): SimpleIndex<LocalDate?> {
        val index = getOrCreateSimpleIndex("localDate", fieldName) {
            SimpleIndex(entries.map { safeGetLocalDate(it[fieldName], dateCache) }, Comparator.naturalOrder())
        }
        return index
    }

    private fun starFieldSearch(fieldName: String, search: (String) -> BitSet): BitSet {
        val allFieldsToCheck = if (fieldName == "*") {
            allFields
        } else {
            setOf(fieldName)
        }
        val result = BitSet()
        for (field in allFieldsToCheck) {
            result.or(search(field))
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrCreateSimpleIndex(
        operation: String,
        fieldName: String,
        indexCreator: () -> SimpleIndex<T>
    ): SimpleIndex<T> {
        val operationCache = synchronized(simpleIndexCache) {
            if (!simpleIndexCache.containsKey(operation)) {
                val newCache = mutableMapOf<String, SimpleIndex<*>>()
                simpleIndexCache[operation] = newCache
                newCache
            } else {
                simpleIndexCache[operation]!!
            }
        }

        synchronized(operationCache){
            val index = if (!operationCache.containsKey(fieldName)) {
                val index = indexCreator()
                operationCache[fieldName] = index
                index
            } else {
                operationCache[fieldName]!!
            }

            return index as SimpleIndex<T>
        }
    }

    private fun getOrCreateFullTextIndex(fieldName: String): FullTextIndex {
        synchronized(fullTextIndexCache) {
            if (fullTextIndexCache.containsKey(fieldName)) {
                return fullTextIndexCache[fieldName]!!
            } else {
                val index = FullTextIndex(entries, fieldName)
                fullTextIndexCache[fieldName] = index
                return index
            }
        }
    }

    private fun safeGetLocalDate(dateText: String?, dateCache: ConcurrentHashMap<String, OffsetDateTime>): LocalDate? {
        if (dateText == null) {
            return null
        }
        try {//TODO cache null
            return getLocalDate(dateText, dateCache)
        } catch (e: DateTimeParseException) {
            return null
        }
    }

    private fun getLocalDate(dateText: String, dataCache: ConcurrentHashMap<String, OffsetDateTime>): LocalDate {
        val offsetDateTime = if (dataCache.containsKey(dateText)) {
            dataCache[dateText]!!
        } else {
            EvaluatorUtil.parseDate(dateText, dataCache)
        }
        return offsetDateTime.toLocalDate()
    }


    private fun getAllFields(entries: List<Map<String, String>>): Set<String> {
        val allFields = mutableSetOf<String>()

        for (entry in entries) {
            for (key in entry.keys) {
                allFields.add(key)
            }
        }

        return allFields
    }

}