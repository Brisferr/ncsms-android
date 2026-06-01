package be.ncsms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import be.ncsms.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("ncsms", Context.MODE_PRIVATE) }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            updatePermissionStatus()
            scheduleSync()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional permission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPrefs()
        updatePermissionStatus()
        updateLastSyncStatus()

        binding.btnSave.setOnClickListener { saveAndSchedule() }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.btnGrantPermission.setOnClickListener {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        observeSyncWork()
    }

    private fun loadPrefs() {
        binding.etServerUrl.setText(prefs.getString("server_url", ""))
        binding.etUsername.setText(prefs.getString("username", ""))
        binding.etPassword.setText(prefs.getString("password", ""))
        val interval = prefs.getLong("interval_hours", 1L)
        binding.spinnerInterval.setSelection(
            when (interval) {
                6L -> 1
                24L -> 2
                else -> 0
            }
        )
    }

    private fun saveAndSchedule() {
        val url = binding.etServerUrl.text.toString().trimEnd('/')
        val user = binding.etUsername.text.toString().trim()
        val pass = binding.etPassword.text.toString()
        val interval = when (binding.spinnerInterval.selectedItemPosition) {
            1 -> 6L
            2 -> 24L
            else -> 1L
        }

        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit()
            .putString("server_url", url)
            .putString("username", user)
            .putString("password", pass)
            .putLong("interval_hours", interval)
            .apply()

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        scheduleSync()
    }

    private fun scheduleSync() {
        if (!hasSmsPermission()) return
        val interval = prefs.getLong("interval_hours", 1L)
        SyncWorker.schedule(this, interval)
    }

    private fun syncNow() {
        if (!hasSmsPermission()) {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            return
        }
        val url = prefs.getString("server_url", "")
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.save_settings_first), Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnSyncNow.isEnabled = false
        binding.progressSync.visibility = View.VISIBLE
        SyncWorker.runNow(this).enqueue()
    }

    private fun observeSyncWork() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("${SyncWorker.WORK_NAME}_manual")
            .observe(this) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        binding.btnSyncNow.isEnabled = false
                        binding.progressSync.visibility = View.VISIBLE
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        binding.btnSyncNow.isEnabled = true
                        binding.progressSync.visibility = View.GONE
                        updateLastSyncStatus()
                        Toast.makeText(this, getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.FAILED -> {
                        binding.btnSyncNow.isEnabled = true
                        binding.progressSync.visibility = View.GONE
                        Toast.makeText(this, getString(R.string.sync_failed), Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        binding.btnSyncNow.isEnabled = true
                        binding.progressSync.visibility = View.GONE
                    }
                }
            }
    }

    private fun updateLastSyncStatus() {
        val lastSync = prefs.getLong("last_sync", 0L)
        val count = prefs.getInt("last_count", 0)
        binding.tvLastSync.text = if (lastSync == 0L) {
            getString(R.string.never_synced)
        } else {
            val date = DateFormat.getDateTimeInstance().format(Date(lastSync))
            getString(R.string.last_sync_info, date, count)
        }
    }

    private fun updatePermissionStatus() {
        val hasPermission = hasSmsPermission()
        binding.tvPermissionStatus.text = if (hasPermission)
            getString(R.string.permission_granted)
        else
            getString(R.string.permission_missing)
        binding.btnGrantPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
        binding.btnSyncNow.isEnabled = hasPermission
    }

    private fun hasSmsPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED
}
