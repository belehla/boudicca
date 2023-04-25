package events.boudicca

import events.boudicca.model.ComplexSearchDto
import events.boudicca.model.Event
import events.boudicca.model.SearchDTO
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path

@ApplicationScoped
@Path("/events")
class EventPublisherResource{

    @Inject
    private lateinit var eventService: EventService

    @GET
    fun list(): Set<Event> {
        return eventService.list()
    }

    @Path("search")
    @POST
    fun search(searchDTO: SearchDTO): Set<Event> {
        return eventService.search(searchDTO)
    }

    @Path("searchBy")
    @POST
    fun searchBy(complexSearchDto: ComplexSearchDto): Set<Event> {
        return eventService.searchBy(complexSearchDto)
    }

}