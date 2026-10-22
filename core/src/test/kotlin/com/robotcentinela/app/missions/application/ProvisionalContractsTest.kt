package com.robotcentinela.app.missions.application

import com.robotcentinela.app.homemapping.application.QueryFloorPlan
import com.robotcentinela.app.homemapping.domain.FloorPlan
import com.robotcentinela.app.homemapping.domain.MapVersionId
import com.robotcentinela.app.homemapping.domain.StationId
import com.robotcentinela.app.homemapping.ports.FloorPlanPort
import com.robotcentinela.app.identity.application.CurrentHomeScope
import com.robotcentinela.app.identity.application.SelectHomeScope
import com.robotcentinela.app.identity.domain.HomeId
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.identity.domain.MembershipRole
import com.robotcentinela.app.identity.domain.MembershipStatus
import com.robotcentinela.app.identity.domain.RobotId
import com.robotcentinela.app.identity.ports.HomeScopePort
import com.robotcentinela.app.missions.domain.MissionId
import com.robotcentinela.app.missions.domain.MissionStatus
import com.robotcentinela.app.missions.domain.MissionSummary
import com.robotcentinela.app.missions.domain.StationOutcome
import com.robotcentinela.app.missions.domain.StationVisit
import com.robotcentinela.app.missions.ports.MissionQueryPort
import com.robotcentinela.app.shared.NotInScopeException
import com.robotcentinela.app.shared.PageRequest
import com.robotcentinela.app.testing.TestFrames
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Contratos provisionales de mapas y misiones (HU05, HU06, HU08). */
class ProvisionalContractsTest {

    private val scopePort = object : HomeScopePort {
        override suspend fun memberships() =
            listOf(HomeMembership(TestFrames.HOME, MembershipRole("reader"), MembershipStatus.Active))

        override suspend fun robotsOf(homeId: HomeId) = listOf(TestFrames.ROBOT)
    }
    private val currentScope = CurrentHomeScope()

    private val mapV1 = MapVersionId(UUID(0, 0x41))
    private val mapV2 = MapVersionId(UUID(0, 0x42))

    private fun mission(robotId: RobotId, vararg outcomes: StationOutcome) = MissionSummary(
        id = MissionId(UUID.randomUUID()),
        robotId = robotId,
        name = "Patrullaje de prueba",
        purpose = null,
        mapVersionId = mapV1,
        startedAt = TestFrames.T0,
        endedAt = null,
        status = MissionStatus.Running,
        stations = outcomes.mapIndexed { i, o -> StationVisit(StationId("E$i"), "Estación $i", o) },
        energy = null,
    )

    @Test
    fun `una estación omitida exige causa`() {
        assertFailsWith<IllegalArgumentException> { StationOutcome.Omitted(" ") }
    }

    @Test
    fun `el resumen cuenta estaciones visitadas y omitidas`() {
        val summary = mission(
            TestFrames.ROBOT_ID,
            StationOutcome.Visited(TestFrames.T0),
            StationOutcome.Omitted("Obstáculo"),
            StationOutcome.Pending,
        )
        assertEquals(3, summary.plannedStations)
        assertEquals(1, summary.visitedStations)
        assertEquals(1, summary.omittedStations)
    }

    @Test
    fun `sin estaciones reportadas el resumen no inventa cero`() {
        val summary = mission(TestFrames.ROBOT_ID).copy(stations = null)
        assertEquals(null, summary.plannedStations)
        assertEquals(null, summary.visitedStations)
    }

    @Test
    fun `no lista misiones de robots ajenos al domicilio`() = runTest {
        SelectHomeScope(scopePort, currentScope)(TestFrames.HOME_ID)
        val port = object : MissionQueryPort {
            override suspend fun missions(robotId: RobotId, page: PageRequest) =
                listOf(mission(TestFrames.ROBOT_ID), mission(TestFrames.OTHER_ROBOT_ID))

            override suspend fun mission(robotId: RobotId, missionId: MissionId): MissionSummary? = null
        }
        val list = ListMissions(port, currentScope)
        assertEquals(1, list(TestFrames.ROBOT_ID, PageRequest(20)).size)
        assertFailsWith<NotInScopeException> { list(TestFrames.OTHER_ROBOT_ID, PageRequest(20)) }
    }

    @Test
    fun `el plano se entrega solo para la versión exacta pedida`() = runTest {
        SelectHomeScope(scopePort, currentScope)(TestFrames.HOME_ID)
        val port = object : FloorPlanPort {
            override suspend fun floorPlan(robotId: RobotId, mapVersionId: MapVersionId) =
                FloorPlan(mapV2, 5.0, 4.0, semantic = true, rooms = emptyList(), walls = emptyList(), stations = emptyList())
        }
        assertNull(QueryFloorPlan(port, currentScope)(TestFrames.ROBOT_ID, mapV1))
    }
}
