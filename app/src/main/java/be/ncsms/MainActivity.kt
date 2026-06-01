package be.ncsms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.*
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("ncsms", Context.MODE_PRIVATE) }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scheduleSync()
        else Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvPermission = findViewById<TextView>(R.id.tv_permission_status)
        val btnGrant = findViewById<Button>(R.id.btn_grant_permission)
        val etUrl = findViewById<EditText>(R.id.et_server_url)
        val etUser = findViewById<EditText>(R.id.et_username)
        val etPass = findViewById<EditText>(R.id.et_password)
        val spinner = findViewById<Spinner>(R.id.spinner_interval)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnSync = findViewById<Button>(R.id.btn_sync_now)
        val progress = findViewById<ProgressBar>(R.id.progress_sync)
        val tvLastSync = findViewById<TextView>(R.id.tv_last_sync)

        // Load saved settings
        etUrl.setText(prefs.getString("server_url", ""))
        etUser.setText(prefs.getString("username", ""))
        etPass.setText(prefs.getString("password", ""))
        val interval = prefs.getLong("interval_hours", 1L)
        spinner.setSelection(when (interval) { 6L -> 1; 24L -> 2; else -> 0 })

        // Permission status
        fun updatePerm() {
            val ok = hasSmsPermission()
            tvPermission.text = if (ok) getString(R.string.permission_granted) else getString(R.string.permission_missing)
            btnGrant.visibility = if (ok) View.GONE else View.VISIBLE
            btnSync.isEnabled = ok
        }
        updatePerm()

        // Last sync info
        val lastSync = prefs.getLong("last_sync", 0L)
        val count = prefs.getInt("last_count", 0)
        tvLastSync.text = if (lastSync == 0L) getString(R.string.never_synced)
            else getString(R.string.last_sync_info, DateFormat.getDateTimeInstance().format(Date(lastSync)), count)

        btnGrant.setOnClickListener {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()
            val hrs = when (spinner.selectedItemPosition) { 1 -> 6L; 2 -> 24L; else -> 1L }
            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("server_url", url)
                .putString("username", user)
                .putString("password", pass)
                .putLong("interval_hours", hrs)
                .apply()
            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
            scheduleSync()
        }

        btnSync.setOnClickListener {
            if (!hasSmsPermission()) { smsPermissionLauncher.launch(Manifest.permission.READ_SMS); return@setOnClickListener }
            if (prefs.getString("server_url", "").isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.save_settings_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSync.isEnabled = false
            progress.visibility = View.VISIBLE

            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(this)
                .enqueueUniqueWork("ncsms_sync_manual", ExistingWorkPolicy.REPLACE, req)

            WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(req.id)
                .observe(this) { info ->
                    if (info == null) return@observe
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            btnSync.isEnabled = true
                            progress.visibility = View.GONE
                            val ls = prefs.getLong("last_sync", 0L)
                            val ct = prefs.getInt("last_count", 0)
                            tvLastSync.text = getString(R.string.last_sync_info, DateFormat.getDateTimeInstance().format(Date(ls)), ct)
                            Toast.makeText(this, getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                        }
                        WorkInfo.State.FAILED -> {
                            btnSync.isEnabled = true
                            progress.visibility = View.GONE
                            Toast.makeText(this, getString(R.string.sync_failed), Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun scheduleSync() {
        if (!hasSmsPermission()) return
        val hrs = prefs.getLong("interval_hours", 1L)
        SyncWorker.schedule(this, hrs)
    }

    private fun hasSmsPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
}
