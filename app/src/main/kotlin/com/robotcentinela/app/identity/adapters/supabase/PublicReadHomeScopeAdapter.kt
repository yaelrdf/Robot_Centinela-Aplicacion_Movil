package com.robotcentinela.app.identity.adapters.supabase

import com.robotcentinela.app.identity.domain.HomeId
import com.robotcentinela.app.identity.domain.HomeMembership
import com.robotcentinela.app.identity.domain.MembershipRole
import com.robotcentinela.app.identity.domain.MembershipStatus
import com.robotcentinela.app.identity.domain.Robot
import com.robotcentinela.app.identity.ports.HomeScopePort
import com.robotcentinela.app.shell.supabase.SupabaseResources
import com.robotcentinela.app.shell.supabase.supabaseCall
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns as PostgrestColumns

/**
 * Adaptador de [HomeScopePort] para la demostración de lectura pública (build `demo`).
 *
 * Sin sesión no hay `memberships` que leer, así que lista los domicilios que RLS deja ver al rol
 * `anon` y les asigna una membresía sintética de solo lectura. No amplía permisos: si backend cierra
 * la lectura anónima, la lista llega vacía. Los robots se consultan igual que en el flujo normal.
 *
 * Es temporal (docs/pendientes.md, B19; docs/decisions/ADR-007-demo-lectura-publica.md).
 */
class PublicReadHomeScopeAdapter(private val supabase: SupabaseClient) : HomeScopePort {

    private val robots = SupabaseHomeScopeAdapter(supabase)

    override suspend fun memberships(): List<HomeMembership> = supabaseCall("domicilios de lectura pública") {
        supabase.from(SupabaseResources.HOMES)
            .select(PostgrestColumns.raw("id, name, timezone"))
            .decodeList<HomeRowDto>()
            .map { HomeMembership(it.toDomain(), MembershipRole(PUBLIC_ROLE), MembershipStatus.Active) }
    }

    override suspend fun robotsOf(homeId: HomeId): List<Robot> = robots.robotsOf(homeId)

    private companion object {
        const val PUBLIC_ROLE = "lectura_publica"
    }
}
