package ai.juanita.app.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RoadRouteContextTest {
    @Test
    fun duplicatePlaceNamesRequireClarification() {
        val candidates = listOf(
            RoutePlace("Springfield", "Illinois", "Sangamon County", "United States", 39.78, -89.64),
            RoutePlace("Springfield", "Massachusetts", "Hampden County", "United States", 42.10, -72.59)
        )

        val result = resolveRoutePlace("Springfield", candidates)

        assertTrue(result is RoutePlaceResolution.Ambiguous)
    }

    @Test
    fun regionHintSelectsMatchingPlace() {
        val candidates = listOf(
            RoutePlace("Springfield", "Illinois", "Sangamon County", "United States", 39.78, -89.64),
            RoutePlace("Springfield", "Massachusetts", "Hampden County", "United States", 42.10, -72.59)
        )

        val result = resolveRoutePlace("Springfield, Massachusetts, United States", candidates)

        assertTrue(result is RoutePlaceResolution.Selected)
        assertEquals("Massachusetts", (result as RoutePlaceResolution.Selected).place.admin1)
    }

    @Test
    fun countryHintAloneDoesNotResolveDuplicateCities() {
        val candidates = listOf(
            RoutePlace("Springfield", "Illinois", "Sangamon County", "United States", 39.78, -89.64),
            RoutePlace("Springfield", "Massachusetts", "Hampden County", "United States", 42.10, -72.59)
        )

        val result = resolveRoutePlace("Springfield, United States", candidates)

        assertTrue(result is RoutePlaceResolution.Ambiguous)
    }

    @Test
    fun uniqueLocalityCanBeSelectedFromNearbySearchResults() {
        val candidates = listOf(
            RoutePlace("Los Cristianos", "Canary Islands", "Santa Cruz de Tenerife", "Spain", 28.05, -16.72),
            RoutePlace("Arona", "Canary Islands", "Santa Cruz de Tenerife", "Spain", 28.10, -16.68),
            RoutePlace("Adeje", "Canary Islands", "Santa Cruz de Tenerife", "Spain", 28.12, -16.73)
        )

        val result = resolveRoutePlace("Los Cristianos", candidates)

        assertTrue(result is RoutePlaceResolution.Selected)
        assertEquals("Los Cristianos", (result as RoutePlaceResolution.Selected).place.name)
    }

    @Test
    fun distanceAndDurationAreReadableAndRoundedUp() {
        assertEquals("1.5 km", RouteText.formatDistance(1499.0, Locale.US))
        assertEquals("999 m", RouteText.formatDistance(999.0, Locale.US))
        assertEquals("1 h 2 min", RouteText.formatDuration(3661.0))
        assertEquals("60 min", RouteText.formatDuration(3599.0))
    }
}
