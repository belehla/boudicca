package events.boudicca.search.query.simple

import events.boudicca.search.query.*

class SimpleEvaluator(private val events: Collection<Map<String, String>>) : Evaluator {
    override fun evaluate(expression: Expression): Collection<Map<String, String>> {
        return events.filter { matchesExpression(expression, it) }
    }

    private fun matchesExpression(expression: Expression, event: Map<String, String>): Boolean {
        when (expression) {
            is EqualsExpression -> {
                if (expression.getFieldName() == "*") {
                    return event.values.any { it.equals(expression.getText(), true) }
                }
                return event.containsKey(expression.getFieldName())
                        && event[expression.getFieldName()].equals(expression.getText(), true)
            }

            is ContainsExpression -> {
                if (expression.getFieldName() == "*") {
                    return event.values.any { it.contains(expression.getText(), true) }
                }
                return event.containsKey(expression.getFieldName())
                        && event[expression.getFieldName()]!!.contains(expression.getText(), true)
            }

            is NotExpression -> {
                return !matchesExpression(expression.getChild(), event)
            }

            is AndExpression -> {
                return matchesExpression(expression.getLeftChild(), event)
                        && matchesExpression(expression.getRightChild(), event)
            }

            is OrExpression -> {
                return matchesExpression(expression.getLeftChild(), event)
                        || matchesExpression(expression.getRightChild(), event)
            }

            else -> {
                throw IllegalStateException("unknown expression kind $expression")
            }
        }
    }
}