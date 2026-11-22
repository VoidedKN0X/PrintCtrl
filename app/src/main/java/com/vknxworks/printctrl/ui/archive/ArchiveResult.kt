package com.vknxworks.printctrl.ui.archive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vknxworks.printctrl.R
import com.vknxworks.printctrl.data.bambuddy.Archive
import com.vknxworks.printctrl.data.bambuddy.BambuddyClient
import com.vknxworks.printctrl.data.bambuddy.archivePhotoUrl
import com.vknxworks.printctrl.data.bambuddy.deleteArchivePhoto
import com.vknxworks.printctrl.data.bambuddy.setArchiveLink
import com.vknxworks.printctrl.data.bambuddy.setVerdict
import com.vknxworks.printctrl.data.bambuddy.uploadArchivePhoto
import com.vknxworks.printctrl.ui.components.ConfirmDialog
import com.vknxworks.printctrl.ui.components.CtrlAlertDialog
import com.vknxworks.printctrl.ui.components.CtrlTextField
import com.vknxworks.printctrl.ui.components.Group
import com.vknxworks.printctrl.ui.components.GroupRow
import com.vknxworks.printctrl.ui.components.Thumb
import com.vknxworks.printctrl.ui.lock.AppLock
import com.vknxworks.printctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** The longest side of a photo that is sent to Bambuddy; larger ones are scaled down first. */
private const val MAX_PHOTO_SIDE = 2560

/**
 * Turns a picked or taken picture into a JPEG file in the app's cache: turned upright, at most
 * [MAX_PHOTO_SIDE] pixels on its longest side, and without the picture's own details (where and when it
 * was taken). Bambuddy only takes .jpg, .png and .webp, and phones often make HEIC.
 */
private fun prepareJpeg(context: Context, uri: Uri): File {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_PHOTO_SIDE) {
            val scale = MAX_PHOTO_SIDE.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }
    val dir = File(context.cacheDir, "photos").also { it.mkdirs() }
    val out = File(dir, "upload-${System.currentTimeMillis()}.jpg")
    try {
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    } catch (e: Throwable) {
        out.delete()
        throw e
    } finally {
        bitmap.recycle()
    }
    return out
}

/**
 * "How did it turn out?" on a finished print: mark it good or failed (Bambuddy's own verdict, with a
 * reason and notes for a failed one) and keep photos of the result. Everything is stored by Bambuddy.
 */
@Composable
fun ResultGroup(api: BambuddyClient, a: Archive, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun run(permission: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toast.makeText(context, e.actionMessage(context, permission), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    fun verdict(value: String?, reason: String? = null, notes: String? = null) = run(R.string.perm_manage_archives) {
        val saved = api.setVerdict(a.id, value, reason, notes)
        // An older Bambuddy ignores the field and answers without it.
        if (saved.userVerdict != value) Toast.makeText(context, R.string.result_not_supported, Toast.LENGTH_LONG).show()
    }

    fun upload(uri: Uri, deleteAfter: File? = null) = run(R.string.perm_manage_archives) {
        var file: File? = null
        try {
            file = withContext(Dispatchers.IO) { prepareJpeg(context, uri) }
            api.uploadArchivePhoto(a.id, file.name, file.asRequestBody("image/jpeg".toMediaType()))
        } finally {
            // Also when the page is left mid-upload (the coroutine is cancelled) or the picture can't be
            // read: neither the prepared copy nor the camera's original (with its location) may stay behind.
            val prepared = file
            withContext(NonCancellable + Dispatchers.IO) {
                prepared?.delete()
                deleteAfter?.delete()
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) upload(uri) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile?.let(::File)
        cameraFile = null
        if (file != null) {
            if (ok && file.length() > 0) upload(Uri.fromFile(file), deleteAfter = file) else file.delete()
        }
    }
    fun launchCamera() {
        runCatching {
            val dir = File(context.cacheDir, "photos").also { it.mkdirs() }
            val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
            cameraFile = file.absolutePath
            AppLock.expectReturn()
            take.launch(FileProvider.getUriForFile(context, context.packageName + ".files", file))
        }.onFailure { Toast.makeText(context, R.string.result_no_camera, Toast.LENGTH_LONG).show() }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, R.string.result_camera_denied, Toast.LENGTH_LONG).show()
    }

    Group(stringResource(R.string.result_title), footer = stringResource(R.string.result_footer)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = a.userVerdict == "good",
                    enabled = !busy,
                    onClick = { verdict(if (a.userVerdict == "good") null else "good") },
                    label = { Text(stringResource(R.string.result_good)) },
                )
                FilterChip(
                    selected = a.userVerdict == "reject",
                    enabled = !busy,
                    onClick = { if (a.userVerdict == "reject") verdict(null) else failing = true },
                    label = { Text(stringResource(R.string.result_failed)) },
                )
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(
                stringResource(
                    when (a.userVerdict) {
                        "good" -> R.string.result_is_good
                        "reject" -> R.string.result_is_failed
                        else -> R.string.result_not_judged
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val photos = a.photos.orEmpty()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                photos.forEach { name ->
                    Thumb(
                        api.archivePhotoUrl(a.id, name),
                        Modifier.size(88.dp).clickable(onClickLabel = stringResource(R.string.result_photo_open)) { viewing = name },
                    )
                }
                TextButton(onClick = { adding = true }, enabled = !busy) { Text(stringResource(R.string.result_photo_add)) }
            }
        }
    }

    if (failing) {
        FailedDialog(a, onDismiss = { failing = false }) { reason, notes ->
            failing = false
            verdict("reject", reason, notes)
        }
    }

    if (adding) {
        CtrlAlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.result_photo_add)) },
            text = {
                Column {
                    GroupRow(stringResource(R.string.result_photo_take), icon = Icons.Outlined.AddAPhoto, onClick = {
                        adding = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                        else {
                            AppLock.expectReturn()
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    })
                    GroupRow(stringResource(R.string.result_photo_pick), icon = Icons.Outlined.PhotoLibrary, onClick = {
                        adding = false
                        AppLock.expectReturn()
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    viewing?.let { name ->
        CtrlAlertDialog(
            onDismissRequest = { viewing = null },
            text = { Thumb(api.archivePhotoUrl(a.id, name), Modifier.fillMaxWidth().aspectRatio(0.8f), ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text(stringResource(R.string.close)) } },
            dismissButton = {
                TextButton(onClick = { viewing = null; deleting = name }) { Text(stringResource(R.string.result_photo_delete), color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    deleting?.let { name ->
        ConfirmDialog(
            stringResource(R.string.result_photo_delete_title),
            stringResource(R.string.result_photo_delete_body),
            stringResource(R.string.result_photo_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            run(R.string.perm_delete_archives) { api.deleteArchivePhoto(a.id, name) }
        }
    }
}

/** Why it failed, and notes: both optional, both saved with the "failed" mark. */
@Composable
private fun FailedDialog(a: Archive, onDismiss: () -> Unit, onConfirm: (reason: String, notes: String) -> Unit) {
    var reason by remember { mutableStateOf(a.failureReason.orEmpty()) }
    var notes by remember { mutableStateOf(a.notes.orEmpty()) }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_failed_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CtrlTextField(reason, { reason = it }, label = { Text(stringResource(R.string.result_reason)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CtrlTextField(
                    notes, { notes = it },
                    label = { Text(stringResource(R.string.archive_notes_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason, notes) }) { Text(stringResource(R.string.result_failed_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Only web links are ever opened: a link is text from the server or from a file. */
fun openWebLink(context: Context, link: String) {
    val uri = Uri.parse(link.trim())
    if (uri.scheme?.lowercase() !in setOf("http", "https")) {
        Toast.makeText(context, R.string.source_not_a_link, Toast.LENGTH_LONG).show()
        return
    }
    runCatching { AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure { Toast.makeText(context, R.string.source_no_browser, Toast.LENGTH_LONG).show() }
}

/**
 * Where the model came from: its MakerWorld page and designer when the file carries them, and a link of
 * your own that you can set, change or clear.
 */
@Composable
fun SourceGroup(api: BambuddyClient, a: Archive, onChanged: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    Group(stringResource(R.string.model_source_title)) {
        a.makerworldUrl?.takeIf { it.isNotBlank() }?.let { url ->
            GroupRow(stringResource(R.string.source_makerworld), url, Icons.Outlined.OpenInNew, onClick = { openWebLink(context, url) })
        }
        a.designer?.takeIf { it.isNotBlank() }?.let { GroupRow(stringResource(R.string.source_designer), it, Icons.Outlined.Person) }
        val own = a.externalUrl?.takeIf { it.isNotBlank() }
        GroupRow(
            stringResource(R.string.source_link),
            own ?: stringResource(R.string.source_link_none),
            Icons.Outlined.Link,
            onClick = { if (own != null) openWebLink(context, own) else editing = true },
        ) {
            TextButton(onClick = { editing = true }) { Text(stringResource(if (own == null) R.string.source_link_set else R.string.source_link_change)) }
        }
    }
    if (editing) {
        LinkDialog(a.externalUrl.orEmpty(), onDismiss = { editing = false }, save = { api.setArchiveLink(a.id, it) }) {
            editing = false
            onChanged()
        }
    }
}

/** A dialog for your own link to a model's page. Blank clears it. */
@Composable
fun LinkDialog(current: String, onDismiss: () -> Unit, save: suspend (String) -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(current) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val trimmed = link.trim()
    val valid = trimmed.isEmpty() || trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
    CtrlAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.source_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.source_link_body))
                CtrlTextField(link, { link = it; error = null }, label = { Text(stringResource(R.string.source_link_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!valid) Text(stringResource(R.string.source_link_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            save(trimmed)
                            onSaved()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            error = e.actionMessage(context, R.string.perm_manage_archives)
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}


/** Where a library file came from: the page Bambuddy imported it from, and a link of your own. */
@Composable
fun LibrarySourceDialog(file: com.vknxworks.printctrl.data.bambuddy.LibraryFile, onDismiss: () -> Unit, save: suspend (String) -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        LinkDialog(file.externalUrl.orEmpty(), onDismiss = { editing = false }, save = save, onSaved = onSaved)
        return
    }
    val imported = file.sourceUrl?.takeIf { it.isNotBlank() }
    val own = file.externalUrl?.takeIf { it.isNotBlank() }
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_source_title)) },
        text = {
            Column {
                if (imported != null) GroupRow(stringResource(R.string.source_imported), imported, Icons.Outlined.OpenInNew, onClick = { openWebLink(context, imported) })
                GroupRow(
                    stringResource(R.string.source_link),
                    own ?: stringResource(R.string.source_link_none),
                    Icons.Outlined.Link,
                    onClick = if (own != null) ({ openWebLink(context, own) }) else null,
                )
            }
        },
        confirmButton = { TextButton(onClick = { editing = true }) { Text(stringResource(if (own == null) R.string.source_link_set else R.string.source_link_change)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
