package base.boudicca.eventdb.service

import base.boudicca.Event
import base.boudicca.eventdb.model.ComplexSearchDto
import base.boudicca.eventdb.model.SearchDTO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


@Service
@Deprecated("this search is superseded by the search service")
class EventSearchService @Autowired constructor(private val entryService: EntryService) {

    fun search(searchDTO: SearchDTO): Set<Event> {
        val fromDate = searchDTO.fromDate
        val toDate = searchDTO.toDate
        return entryService.all().asSequence().mapNotNull { Event.fromEntry(it) }
            .filter { e -> fromDate == null || !e.startDate.isBefore(fromDate) }
            .filter { e -> toDate == null || !e.startDate.isAfter(toDate) }
            .filter { e ->
                val data = e.data
                searchDTO.name == null || e.name.lowercase().contains(searchDTO.name.lowercase())
                        || (data.values.any { it.lowercase().contains(searchDTO.name.lowercase()) })
            }
            .toSet()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun searchBy(searchDto: ComplexSearchDto): Set<Event> {
        val filters = mutableSetOf<(Event) -> Boolean>()
        if (!searchDto.anyKeyExactMatch.isNullOrEmpty())
            filters.add { event -> searchDto.anyKeyExactMatch.any { key -> event.data.containsKey(key) } }
        if (!searchDto.allKeyExactMatch.isNullOrEmpty())
            filters.add { event -> searchDto.allKeyExactMatch.all { key -> event.data.containsKey(key) } }
        if (!searchDto.anyKeyOrValueContains.isNullOrEmpty())
            filters.add { event ->
                searchDto.anyKeyOrValueContains.any {
                    event.data.keys.any { key -> key.contains(it.lowercase()) } ||
                            event.data.values.any { value -> value.contains(it.lowercase()) }
                }
            }
        if (!searchDto.allKeyOrValueContains.isNullOrEmpty())
            filters.add { event ->
                searchDto.allKeyOrValueContains.all {
                    event.data.keys.any { key -> key.contains(it.lowercase()) } ||
                            event.data.values.any { value -> value.contains(it.lowercase()) }
                }
            }
        if (!searchDto.anyKeyOrValueExactMatch.isNullOrEmpty())
            filters.add { event ->
                searchDto.anyKeyOrValueExactMatch.any {
                    event.data.containsKey(it.lowercase()) || event.data.containsValue(it.lowercase())
                }
            }
        if (!searchDto.allKeyOrValueExactMatch.isNullOrEmpty())
            filters.add { event ->
                searchDto.allKeyOrValueExactMatch.all {
                    event.data.containsKey(it.lowercase()) || event.data.containsValue(it.lowercase())
                }
            }
        if (!searchDto.anyValueForKeyContains.isNullOrEmpty())
            filters.add { event ->
                searchDto.anyValueForKeyContains.any { (key, value) ->
                    event.data[key]?.lowercase()?.contains(value) ?: false
                }
            }
        if (!searchDto.allValueForKeyContains.isNullOrEmpty())
            filters.add { event ->
                searchDto.allValueForKeyContains.all { (key, value) ->
                    event.data[key]?.lowercase()?.contains(value) ?: false
                }
            }
        if (!searchDto.anyValueForKeyExactMatch.isNullOrEmpty())
            filters.add { event ->
                searchDto.anyValueForKeyExactMatch.any { (key, value) ->
                    event.data[key]?.lowercase()?.equals(value) ?: false
                }
            }
        if (!searchDto.allValueForKeyExactMatch.isNullOrEmpty())
            filters.add { event ->
                searchDto.allValueForKeyExactMatch.all { (key, value) ->
                    event.data[key]?.lowercase()?.equals(value) ?: false
                }
            }

        return filters
            .fold(
                entryService.all().mapNotNull { Event.fromEntry(it) }.toSet()
            )
            { events, currentFilter ->
                events.filter(currentFilter).toSet()
            }
            .toSet()
    }
}