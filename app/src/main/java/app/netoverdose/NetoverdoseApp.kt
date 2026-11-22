package app.netoverdose

import android.app.Application
import androidx.room.Room
import app.netoverdose.data.AppDatabase
import app.netoverdose.data.MIGRATION_1_2
import app.netoverdose.data.MIGRATION_2_3
import app.netoverdose.data.TaskRepository
import app.netoverdose.design.DesignController
import app.netoverdose.notify.Reminders
import app.netoverdose.ui.theme.installFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
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

    /** One-shot user messages produced outside of screens (share intake, workers). */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installFonts(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "netoverdose.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        reminders = Reminders(this)
        reminders.createChannel()
        repository = TaskRepository(this, database, reminders)
        designController = DesignController(this)

        // Safety net: re-plan reminders from Room in case the OEM dropped persisted work.
        appScope.launch {
            runCatching { reminders.reschedule(database.tasks().openOnce()) }
        }
    }
}
