package bo.piyo.app

import android.app.Application
import bo.piyo.app.di.AppContainer
import bo.piyo.app.di.AppModule

class PiyoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppModule.create(this)
    }
}
