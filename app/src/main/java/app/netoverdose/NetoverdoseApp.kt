package app.netoverdose

import android.app.Application
import androidx.room.Room
import app.netoverdose.data.AppDatabase
import app.netoverdose.data.MIGRATION_1_2
import app.netoverdose.data.MIGRATION_2_3
import app.netoverdose.data.MIGRATION_3_4
import app.netoverdose.data.StoredAttachment
import app.netoverdose.data.TaskRepository
import app.netoverdose.design.DesignController
import app.netoverdose.notify.Reminders
import app.netoverdose.sound.Sounds
import app.netoverdose.ui.theme.installFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

class NetoverdoseApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var reminders: Reminders
        private set

    lateinit var repository: TaskRepository
        private set

    lateinit var designController: DesignController
        private set

    /** Synthesised UI tones; shared so any screen can play without holding a Context. */
    lateinit var sounds: Sounds
        private set

    /** One-shot user messages produced outside of screens (share intake, workers). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * A shared file waiting for the intake panel (A2). It is a copy in private storage;
     * nothing is persisted as a task until the user saves the form.
     *
     * A StateFlow, not a Channel: the file is written before the splash releases AppRoot
     * (StarfallSplash holds 2600 ms), so the value must survive with no collector, and the
     * event has to remain readable after the activity is recreated. More than one observer
     * looks at it — AppRoot only switches to Main, MainScreen takes and clears it — so a
     * single-consumer queue would be handed to whichever observer collected first, and
     * cancelling any collector of a Channel closes it for the whole Application.
     * Consumption happens in exactly one place: [takeShare].
     */
    private val shareHolder = MutableStateFlow<StoredAttachment?>(null)

    val pendingShare: StateFlow<StoredAttachment?> = shareHolder.asStateFlow()

    /** Publishes a prepared copy for the intake panel. Called from the share entry point. */
    fun postShare(stored: StoredAttachment) {
        shareHolder.value = stored
    }

    /**
     * Returns the pending copy and clears it, so only one caller ever opens the form for it.
     * MainScreen is the only intended caller; AppRoot just watches the value.
     */
    fun takeShare(): StoredAttachment? = shareHolder.getAndUpdate { null }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installFonts(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "netoverdose.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        reminders = Reminders(this)
        reminders.createChannel()
        repository = TaskRepository(this, database, reminders)
        designController = DesignController(this)
        sounds = Sounds(this)

        // Safety net: re-plan reminders from Room in case the OEM dropped persisted work.
        appScope.launch {
            runCatching { reminders.reschedule(database.tasks().openOnce()) }
        }
    }
}
