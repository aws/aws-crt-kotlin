package aws.sdk.kotlin.crt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public interface CrtShutdownHandle { public val id: String }

@OptIn(ExperimentalUuidApi::class)
internal data class CrtShutdownHandleImpl(override val id: String = Uuid.random().toString()) : CrtShutdownHandle

internal class ShutdownHandleManager() {
    private val mutex = Mutex() // for handles
    private val handles = mutableSetOf<CrtShutdownHandle>()

    val hasActiveHandles: Boolean
        get() = handles.isNotEmpty()

    suspend fun acquire(): CrtShutdownHandle {
        val handle = CrtShutdownHandleImpl()
        mutex.withLock { handles += handle }
        return handle
    }

    suspend fun release(handle: CrtShutdownHandle): Boolean = mutex.withLock {
        handles.remove(handle)
    }
}