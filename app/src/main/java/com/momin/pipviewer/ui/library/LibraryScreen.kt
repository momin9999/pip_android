@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.momin.pipviewer.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.itemsIndexed as lazyRowItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.momin.pipviewer.R
import com.momin.pipviewer.data.FavoriteShelf
import com.momin.pipviewer.data.FolderContents
import com.momin.pipviewer.data.FolderRef
import com.momin.pipviewer.data.VideoItem
import com.momin.pipviewer.ui.theme.StarYellow
import com.momin.pipviewer.util.formatDuration
import com.momin.pipviewer.util.formatSize
import com.momin.pipviewer.util.rememberVideoThumbnail

private val CARD_SHAPE = RoundedCornerShape(18.dp)
private val GRID_MIN_CELL = 220.dp

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onPlay: (List<VideoItem>, Int) -> Unit,
) {
    val roots by vm.roots.collectAsStateWithLifecycle()
    val starred by vm.starred.collectAsStateWithLifecycle()
    val shelves by vm.favoriteShelves.collectAsStateWithLifecycle()
    val starredKeys by vm.starredKeys.collectAsStateWithLifecycle()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) vm.addRoot(uri) }

    AnimatedContent(
        targetState = vm.current,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "library-nav",
    ) { folder ->
        if (folder == null) {
            HomeScreen(
                roots = roots,
                shelves = shelves,
                hasFavorites = starred.isNotEmpty(),
                starredKeys = starredKeys,
                onAddFolder = { pickFolder.launch(null) },
                onOpenFolder = vm::openFolder,
                onToggleStar = vm::setStarred,
                onRemoveRoot = vm::removeRoot,
                onPlay = onPlay,
            )
        } else {
            FolderScreen(
                folder = folder,
                contents = vm.contents,
                loading = vm.loading,
                starredKeys = starredKeys,
                onBack = { vm.goBack() },
                onOpenFolder = vm::openFolder,
                onToggleStar = vm::setStarred,
                onPlay = onPlay,
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Home                                                                                          */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun HomeScreen(
    roots: List<FolderRef>,
    shelves: List<FavoriteShelf>,
    hasFavorites: Boolean,
    starredKeys: Set<String>,
    onAddFolder: () -> Unit,
    onOpenFolder: (FolderRef) -> Unit,
    onToggleStar: (FolderRef, Boolean) -> Unit,
    onRemoveRoot: (FolderRef) -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
) {
    if (roots.isEmpty() && !hasFavorites) {
        EmptyLibrary(onAddFolder = onAddFolder)
        return
    }

    // stringResource() is @Composable, so resolve labels here — the grid content lambda is not.
    val foldersLabel = stringResource(R.string.folders)
    val emptyShelfLabel = stringResource(R.string.folder_empty)

    GridScaffold(title = stringResource(R.string.library)) {
        shelves.forEach { shelf ->
            favoriteShelf(
                shelf = shelf,
                emptyLabel = emptyShelfLabel,
                onOpenFolder = onOpenFolder,
                onToggleStar = onToggleStar,
                onPlay = onPlay,
            )
        }

        sectionHeader(foldersLabel)
        items(roots, key = { "root_" + it.key }) { f ->
            FolderCard(
                name = f.name,
                starred = starredKeys.contains(f.key),
                onClick = { onOpenFolder(f) },
                onToggleStar = { onToggleStar(f, it) },
                onRemove = { onRemoveRoot(f) },
            )
        }
        item(key = "add_folder") { AddFolderCard(onClick = onAddFolder) }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Favorites shelves — a starred folder's inner folders + videos shown together on the home page  */
/* --------------------------------------------------------------------------------------------- */

private val SHELF_ITEM_WIDTH = 188.dp

private fun LazyGridScope.favoriteShelf(
    shelf: FavoriteShelf,
    emptyLabel: String,
    onOpenFolder: (FolderRef) -> Unit,
    onToggleStar: (FolderRef, Boolean) -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
) {
    item(key = "shelf_head_" + shelf.folder.key, span = { GridItemSpan(maxLineSpan) }) {
        ShelfHeader(
            folder = shelf.folder,
            videoCount = shelf.contents.videos.size,
            onOpen = { onOpenFolder(shelf.folder) },
            onUnstar = { onToggleStar(shelf.folder, false) },
        )
    }
    item(key = "shelf_row_" + shelf.folder.key, span = { GridItemSpan(maxLineSpan) }) {
        if (shelf.contents.isEmpty) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 10.dp),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
            ) {
                lazyRowItems(shelf.contents.folders, key = { "sf_" + it.key }) { f ->
                    ShelfFolderCard(name = f.name, onClick = { onOpenFolder(f) })
                }
                lazyRowItemsIndexed(shelf.contents.videos, key = { _, v -> "sv_" + v.uri }) { index, video ->
                    ShelfVideoCard(video = video, onClick = { onPlay(shelf.contents.videos, index) })
                }
            }
        }
    }
}

@Composable
private fun ShelfHeader(
    folder: FolderRef,
    videoCount: Int,
    onOpen: () -> Unit,
    onUnstar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingSquare(tint = StarYellow, size = 36.dp) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = StarYellow, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (videoCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.video_count, videoCount, videoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = stringResource(R.string.see_all),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        StarToggle(starred = true, onToggle = { onUnstar() })
    }
}

@Composable
private fun ShelfVideoCard(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(SHELF_ITEM_WIDTH)
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        VideoThumbnailBox(
            video = video,
            iconSize = 30.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Text(
            text = video.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun ShelfFolderCard(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(SHELF_ITEM_WIDTH)
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun EmptyLibrary(onAddFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(340.dp),
            )
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(
                onClick = onAddFolder,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_folder), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Folder browser                                                                                */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun FolderScreen(
    folder: FolderRef,
    contents: FolderContents,
    loading: Boolean,
    starredKeys: Set<String>,
    onBack: () -> Unit,
    onOpenFolder: (FolderRef) -> Unit,
    onToggleStar: (FolderRef, Boolean) -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    // stringResource() is @Composable, so resolve labels here — the grid content lambda is not.
    val foldersLabel = stringResource(R.string.folders)
    val videosLabel = stringResource(R.string.videos)

    GridScaffold(
        title = folder.name,
        onBack = onBack,
        actions = {
            StarToggle(
                starred = starredKeys.contains(folder.key),
                onToggle = { onToggleStar(folder, it) },
            )
        },
    ) {
        if (loading && contents.isEmpty) {
            fullSpanBox { CircularProgressIndicator(strokeWidth = 2.5.dp) }
            return@GridScaffold
        }
        if (contents.isEmpty) {
            fullSpan {
                Column(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.folder_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@GridScaffold
        }

        if (contents.folders.isNotEmpty()) {
            sectionHeader(foldersLabel)
            items(contents.folders, key = { "f_" + it.key }) { f ->
                FolderCard(
                    name = f.name,
                    starred = starredKeys.contains(f.key),
                    onClick = { onOpenFolder(f) },
                    onToggleStar = { onToggleStar(f, it) },
                )
            }
        }

        if (contents.videos.isNotEmpty()) {
            sectionHeader(videosLabel)
            itemsIndexed(contents.videos, key = { _, v -> "v_" + v.uri }) { index, video ->
                VideoTile(video = video, onClick = { onPlay(contents.videos, index) })
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Grid scaffold + helpers                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun GridScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyGridScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Rounded.ChevronLeft,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(GRID_MIN_CELL),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private fun LazyGridScope.sectionHeader(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 2.dp),
        )
    }
}

private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.fullSpanBox(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun FolderCard(
    name: String,
    starred: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    onToggleStar: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(122.dp)
                .clip(CARD_SHAPE)
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRemove != null) {
                        { menuOpen = true }
                    } else null,
                )
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingSquare(tint = accent, size = 40.dp) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                StarToggle(starred = starred, onToggle = onToggleStar)
            }
            Spacer(Modifier.weight(1f))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onRemove != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_library)) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun AddFolderCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp)
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LeadingSquare(tint = MaterialTheme.colorScheme.primary, size = 40.dp) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.add_folder),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun VideoTile(video: VideoItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        VideoThumbnailBox(
            video = video,
            iconSize = 34.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Text(
                video.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                formatSize(video.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shared 16:9 cover box: thumbnail (or placeholder), a soft play affordance, and a duration badge. */
@Composable
private fun VideoThumbnailBox(video: VideoItem, iconSize: Dp, modifier: Modifier) {
    val thumb = rememberVideoThumbnail(video.uri)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb?.bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
        // soft play affordance
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        val dur = thumb?.durationMs ?: 0L
        if (dur > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(formatDuration(dur), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun LeadingSquare(tint: Color, size: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.4f))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun StarToggle(starred: Boolean, onToggle: (Boolean) -> Unit) {
    IconButton(onClick = { onToggle(!starred) }) {
        Icon(
            imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = stringResource(if (starred) R.string.unstar else R.string.star),
            tint = if (starred) StarYellow else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
    }
}
