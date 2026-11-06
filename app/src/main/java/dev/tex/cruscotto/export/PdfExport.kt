package dev.tex.cruscotto.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.tex.cruscotto.R
import dev.tex.cruscotto.data.Repository
import dev.tex.cruscotto.session.Session
import dev.tex.cruscotto.ui.LocalSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Returns a function that asks where to save, then writes the PDF of a year's transactions there.
 * The PDF is built on a background thread and written straight to the chosen file.
 */
@Composable
fun rememberYearPdfExport(repo: Repository): (Int) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var pendingYear by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val year = pendingYear
        if (uri == null || year == 0) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = try {
                val txns = repo.txnsOfYear(year)
                val cats = repo.categoriesOnce()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { YearPdf(context).write(it, year, txns, cats) }
                        ?: error("no output stream")
                }
                true
            } catch (e: Exception) {
                false
            }
            snackbar.showSnackbar(context.getString(if (ok) R.string.msg_pdf_saved else R.string.msg_pdf_failed))
        }
    }
    return remember(launcher) {
        { year: Int ->
            pendingYear = year
            Session.suspendAutoLock = true
            launcher.launch(context.getString(R.string.pdf_file_name, year))
        }
    }
}
