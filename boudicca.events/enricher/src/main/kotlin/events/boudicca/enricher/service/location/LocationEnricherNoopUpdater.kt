package events.boudicca.enricher.service.location

class LocationEnricherNoopUpdater : LocationEnricherUpdater {
    override fun updateData(): List<LocationData> = emptyList()
}
