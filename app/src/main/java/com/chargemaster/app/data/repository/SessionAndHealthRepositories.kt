package com.chargemaster.app.data.repository

import com.chargemaster.app.core.database.BatteryHealthDao
import com.chargemaster.app.core.database.ChargingSessionDao
import com.chargemaster.app.data.local.toDomain
import com.chargemaster.app.data.local.toEntity
import com.chargemaster.app.domain.model.BatteryHealthSnapshot
import com.chargemaster.app.domain.model.ChargingSession
import com.chargemaster.app.domain.repository.HealthRepository
import com.chargemaster.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(private val dao: ChargingSessionDao) : SessionRepository {
    override suspend fun openSession() = dao.open()?.toDomain()
    override fun observeOpenSession() = dao.observeOpen().map { it?.toDomain() }
    override suspend fun lastClosedSession() = dao.lastClosed()?.toDomain()
    override fun observeRecent(limit: Int) = dao.observeRecent(limit).map { l -> l.map { it.toDomain() } }
    override fun observeSince(since: Long) = dao.observeSince(since).map { l -> l.map { it.toDomain() } }
    override suspend fun recent(limit: Int) = dao.recent(limit).map { it.toDomain() }
    override fun observeById(id: Long) = dao.observeById(id).map { it?.toDomain() }
    override suspend fun save(session: ChargingSession): Long =
        if (session.id == 0L) dao.insert(session.toEntity()) else { dao.update(session.toEntity()); session.id }
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun pruneOlderThan(before: Long) = dao.deleteOlderThan(before)
    override suspend fun clear() = dao.clear()
}

@Singleton
class HealthRepositoryImpl @Inject constructor(private val dao: BatteryHealthDao) : HealthRepository {
    override fun observeSnapshots() = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun insert(snapshot: BatteryHealthSnapshot) { dao.insert(snapshot.toEntity()) }
    override suspend fun clear() = dao.clear()
}
