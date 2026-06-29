package co.eskala.bktpj_notification

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.eskala.bktpj_notification.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Prefs.initDefaults(this)
        loadSavedValues()

        b.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        b.btnFetchCols.setOnClickListener { fetchColumns() }
        b.btnTest.setOnClickListener { testConnection() }
        b.btnSave.setOnClickListener { saveConfig() }
    }

    private fun loadSavedValues() {
        val host = Prefs.getNeonHost(this)
        if (host.isNotEmpty()) {
            b.etConnStr.hint = "postgresql://${Prefs.getNeonUser(this)}:***@$host/${Prefs.getNeonDb(this)}"
        }
        b.etInboxId.setText(Prefs.getColumnInbox(this))
        b.etHoyId.setText(Prefs.getColumnHoy(this))
        b.etNuPackage.setText(Prefs.getNuPackage(this))
    }

    private fun saveConfig() {
        val connStr = b.etConnStr.text.toString().trim()
        if (connStr.isNotEmpty()) {
            if (!Prefs.parseAndSaveConnectionString(this, connStr)) {
                Toast.makeText(this, "URL inválida — debe ser postgresql://user:pass@host/db", Toast.LENGTH_LONG).show()
                return
            }
            b.etConnStr.text?.clear()
        }
        Prefs.setColumnInbox(this, b.etInboxId.text.toString().trim())
        Prefs.setColumnHoy(this, b.etHoyId.text.toString().trim())
        Prefs.setNuPackage(this, b.etNuPackage.text.toString().trim().ifEmpty { "com.nu.production" })
        b.tvStatus.text = "Configuración guardada"
        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        loadSavedValues()
    }

    private fun testConnection() {
        Toast.makeText(this, "Conectando...", Toast.LENGTH_SHORT).show()
        status("Probando conexión Neon...")
        lifecycleScope.launch {
            val r = NeonRepository.testConnection(this@MainActivity)
            val msg = if (r.isSuccess) "Conexión OK" else "Error: ${r.exceptionOrNull()?.message}"
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            status(msg)
        }
    }

    private fun fetchColumns() {
        Toast.makeText(this, "Obteniendo columnas...", Toast.LENGTH_SHORT).show()
        status("Obteniendo columnas...")
        lifecycleScope.launch {
            val r = NeonRepository.fetchColumns(this@MainActivity)
            if (r.isSuccess) {
                val cols = r.getOrNull() ?: emptyList()
                val text = cols.joinToString("\n") { (id, label) -> "$label\n  $id" }
                Toast.makeText(this@MainActivity, "${cols.size} columnas encontradas", Toast.LENGTH_SHORT).show()
                status(text)
            } else {
                val msg = "Error: ${r.exceptionOrNull()?.message}"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                status(msg)
            }
        }
    }

    private fun status(msg: String) {
        b.cardStatus.visibility = View.VISIBLE
        b.tvStatus.text = msg
    }
}
