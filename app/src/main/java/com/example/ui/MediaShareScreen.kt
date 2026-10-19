package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.model.DownloadProgress
import com.example.model.DownloadState
import com.example.model.DownloadedItem
import com.example.share.NativeShareHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaShareScreen(
  viewModel: MediaShareViewModel,
  modifier: Modifier = Modifier
) {
  val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
  val downloadsList by viewModel.downloadsList.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var itemToDelete by remember { mutableStateOf<DownloadedItem?>(null) }
  var inputUrlText by rememberSaveable { mutableStateOf("") }

  // Auto-launch native Android share sheet when download succeeds from a share intent
  LaunchedEffect(downloadState) {
    val state = downloadState
    if (state is DownloadState.Success && state.autoShareTriggered) {
      NativeShareHelper.shareFile(context, state.item)
      viewModel.markAutoShareConsumed()
    }
  }

  // Delete confirmation dialog
  if (itemToDelete != null) {
    val item = itemToDelete!!
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text(text = stringResource(id = R.string.dialog_delete_title)) },
      text = {
        Text(
          text = stringResource(id = R.string.dialog_delete_message, item.fileName)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteDownload(item)
            itemToDelete = null
            Toast.makeText(context, context.getString(R.string.toast_deleted), Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_button")
        ) {
          Text(text = stringResource(id = R.string.dialog_confirm))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { itemToDelete = null },
          modifier = Modifier.testTag("cancel_delete_button")
        ) {
          Text(text = stringResource(id = R.string.dialog_cancel))
        }
      }
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 20.sp
                )
              )
              Text(
                text = stringResource(id = R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall.copy(
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp
                )
              )
            }
          }
        },
        actions = {
          if (downloadState !is DownloadState.Idle) {
            IconButton(
              onClick = { viewModel.clearState() },
              modifier = Modifier.testTag("clear_state_top_button")
            ) {
              Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = stringResource(id = R.string.action_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("bottom_nav_bar")
      ) {
        NavigationBarItem(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          icon = { Icon(Icons.Default.Download, contentDescription = null) },
          label = { Text(stringResource(id = R.string.tab_share)) },
          modifier = Modifier.testTag("nav_tab_share")
        )
        NavigationBarItem(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          icon = { Icon(Icons.Default.Folder, contentDescription = null) },
          label = { Text("${stringResource(id = R.string.tab_library)} (${downloadsList.size})") },
          modifier = Modifier.testTag("nav_tab_library")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      if (selectedTab == 0) {
        // Tab 0: Share & Active Downloader
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .testTag("downloader_tab_content"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Status Card: Resolving, Downloading, Success, Error
          item {
            when (val state = downloadState) {
              is DownloadState.Idle -> {
                IdleInstructionCard()
              }
              is DownloadState.Resolving -> {
                ResolvingCard(url = state.url, onCancel = { viewModel.cancelDownload() })
              }
              is DownloadState.Downloading -> {
                DownloadingCard(
                  url = state.url,
                  progress = state.progress,
                  onCancel = { viewModel.cancelDownload() }
                )
              }
              is DownloadState.Success -> {
                SuccessCard(
                  item = state.item,
                  onShare = { NativeShareHelper.shareFile(context, state.item) },
                  onOpen = { NativeShareHelper.openFile(context, state.item) },
                  onDismiss = { viewModel.clearState() }
                )
              }
              is DownloadState.Error -> {
                ErrorCard(
                  error = state,
                  onRetry = { viewModel.retry() },
                  onOpenBrowser = {
                    if (state.sourceUrl != null) openBrowser(context, state.sourceUrl)
                  },
                  onDismiss = { viewModel.clearState() }
                )
              }
              is DownloadState.Cancelled -> {
                CancelledCard(
                  onRetry = { viewModel.retry() },
                  onDismiss = { viewModel.clearState() }
                )
              }
            }
          }

          // Direct URL Entry Field
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("direct_url_entry_card"),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  text = "Direct URL Download",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Download and re-share any image or video web link directly:",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                  value = inputUrlText,
                  onValueChange = { inputUrlText = it },
                  placeholder = { Text(stringResource(id = R.string.input_url_hint), fontSize = 13.sp) },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                  ),
                  keyboardActions = KeyboardActions(
                    onGo = {
                      if (inputUrlText.isNotBlank()) {
                        keyboardController?.hide()
                        viewModel.startDownload(inputUrlText, autoShare = true)
                      }
                    }
                  ),
                  trailingIcon = {
                    if (inputUrlText.isNotEmpty()) {
                      IconButton(onClick = { inputUrlText = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear input")
                      }
                    }
                  },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_text_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                  onClick = {
                    if (inputUrlText.isNotBlank()) {
                      keyboardController?.hide()
                      viewModel.startDownload(inputUrlText, autoShare = true)
                    }
                  },
                  enabled = inputUrlText.isNotBlank(),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_download_button")
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(stringResource(id = R.string.action_download))
                }
              }
            }
          }
        }
      } else {
        // Tab 1: Private Downloads Library
        if (downloadsList.isEmpty()) {
          EmptyLibraryView()
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("downloads_library_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Private Storage Files (${downloadsList.size})",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Stored internally",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                )
              }
            }

            items(downloadsList, key = { it.id }) { item ->
              DownloadedItemCard(
                item = item,
                onShare = { NativeShareHelper.shareFile(context, item) },
                onOpen = { NativeShareHelper.openFile(context, item) },
                onDelete = { itemToDelete = item }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun IdleInstructionCard() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("idle_instruction_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = stringResource(id = R.string.waiting_for_share_title),
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp
        ),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = stringResource(id = R.string.waiting_for_share_subtitle),
        style = MaterialTheme.typography.bodySmall.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 18.sp
        ),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(16.dp))

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Workflow: Share in any app → Select MediaShare → Auto-downloads → Opens native share sheet.",
            style = MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )
          )
        }
      }
    }
  }
}

@Composable
fun ResolvingCard(url: String, onCancel: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("resolving_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp),
          strokeWidth = 2.5.dp,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = stringResource(id = R.string.status_resolving),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = url,
            style = MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontFamily = FontFamily.Monospace
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("cancel_resolve_button")
      ) {
        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(id = R.string.action_cancel))
      }
    }
  }
}

@Composable
fun DownloadingCard(
  url: String,
  progress: DownloadProgress,
  onCancel: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("downloading_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .padding(18.dp)
        .animateContentSize()
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = stringResource(id = R.string.status_downloading),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        if (progress.percentage != null) {
          Text(
            text = "${(progress.percentage * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = url,
        style = MaterialTheme.typography.bodySmall.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = FontFamily.Monospace
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (progress.percentage != null) {
        LinearProgressIndicator(
          progress = { progress.percentage },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .testTag("download_progress_bar"),
          color = MaterialTheme.colorScheme.primary
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .testTag("download_progress_bar_indeterminate"),
          color = MaterialTheme.colorScheme.primary
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = formatFileSize(progress.bytesDownloaded),
          style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        if (progress.totalBytes > 0) {
          Text(
            text = "of ${formatFileSize(progress.totalBytes)}",
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )
        } else {
          Text(
            text = "Streaming…",
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("cancel_download_button")
      ) {
        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(id = R.string.action_cancel))
      }
    }
  }
}

@Composable
fun SuccessCard(
  item: DownloadedItem,
  onShare: () -> Unit,
  onOpen: () -> Unit,
  onDismiss: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("success_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Media Ready & Saved",
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = Color(0xFF1B5E20)
            )
          )
        }

        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.Clear, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Media Preview if Image
      if (item.isImage) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = File(item.filePath),
            contentDescription = "Downloaded image preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }
        Spacer(modifier = Modifier.height(12.dp))
      } else if (item.isVideo) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF212121)),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Video Ready to Share",
              color = Color.White,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
      }

      Text(
        text = item.fileName,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "${item.mimeType} • ${formatFileSize(item.sizeBytes)}",
        style = MaterialTheme.typography.labelSmall.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onShare,
          modifier = Modifier
            .weight(1.2f)
            .testTag("action_share_button"),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(id = R.string.action_share))
        }

        OutlinedButton(
          onClick = onOpen,
          modifier = Modifier
            .weight(1f)
            .testTag("action_open_button")
        ) {
          Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(id = R.string.action_open))
        }
      }
    }
  }
}

@Composable
fun ErrorCard(
  error: DownloadState.Error,
  onRetry: () -> Unit,
  onOpenBrowser: () -> Unit,
  onDismiss: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("download_error_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Download Failed",
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          )
        }

        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = error.reason,
        style = MaterialTheme.typography.bodySmall.copy(
          color = MaterialTheme.colorScheme.onErrorContainer,
          lineHeight = 18.sp
        )
      )

      if (error.isHtmlError) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(id = R.string.error_html_received),
          style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 15.sp
          )
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onRetry,
          modifier = Modifier
            .weight(1f)
            .testTag("retry_download_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          )
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(id = R.string.action_retry))
        }

        if (error.sourceUrl != null) {
          OutlinedButton(
            onClick = onOpenBrowser,
            modifier = Modifier
              .weight(1f)
              .testTag("open_browser_fallback_button")
          ) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("In Browser")
          }
        }
      }
    }
  }
}

@Composable
fun CancelledCard(
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("cancelled_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Download Cancelled",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "The download operation was cancelled. Partial files have been safely cleaned up.",
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )
      Spacer(modifier = Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRetry) {
          Text(stringResource(id = R.string.action_retry))
        }
        OutlinedButton(onClick = onDismiss) {
          Text(stringResource(id = R.string.action_clear))
        }
      }
    }
  }
}

@Composable
fun DownloadedItemCard(
  item: DownloadedItem,
  onShare: () -> Unit,
  onOpen: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("downloaded_item_${item.id}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Thumbnail
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        if (item.isImage) {
          AsyncImage(
            model = File(item.filePath),
            contentDescription = item.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        } else if (item.isVideo) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
          )
        } else {
          Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.fileName,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${formatFileSize(item.sizeBytes)} • ${formatTimestamp(item.downloadTimestamp)}",
          style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
          text = item.mimeType,
          style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
          )
        )
      }

      // Actions: Share, Open, Delete
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onShare,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
        }
        IconButton(
          onClick = onOpen,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun EmptyLibraryView() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(32.dp)
      .testTag("empty_library_view"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(36.dp)
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(id = R.string.library_empty_title),
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(id = R.string.library_empty_desc),
      style = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
      ),
      textAlign = TextAlign.Center
    )
  }
}

private fun openBrowser(context: Context, url: String) {
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
  } catch (e: Exception) {
    Toast.makeText(context, "Cannot open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
  }
}

private fun formatTimestamp(millis: Long): String {
  if (millis == 0L) return ""
  val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
  return sdf.format(Date(millis))
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0) return "0 KB"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  return if (mb >= 1.0) {
    String.format(Locale.US, "%.1f MB", mb)
  } else {
    String.format(Locale.US, "%.1f KB", kb)
  }
}
