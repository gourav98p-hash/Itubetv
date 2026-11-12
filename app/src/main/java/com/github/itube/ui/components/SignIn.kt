package com.github.itube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Refresh
import com.github.itube.ui.components.EqualizerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.github.itube.MusicCacheManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.TrackBlurPopup
import com.github.itube.ui.components.popupContainerColor
import com.github.itube.ui.components.popupScrimColor
import com.github.itube.ui.components.BlurSetting
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&hl=en" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Den%26next%3D%252F"

/**
 * Google's own sign-in page. AirStream never sees the password; once the page lands back on
 * YouTube signed in, the youtube.com cookies are taken over (encrypted) and the WebView's
 * copy is wiped.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Cancel")
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("Sign in to YouTube", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("accounts.google.com · kept encrypted on this phone", color = c.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val cookies = CookieManager.getInstance()
                    // Start clean so a half-finished earlier attempt can't leak in.
                    cookies.removeAllCookies(null)
                    cookies.setAcceptCookie(true)
                    WebView(context).apply {
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Google refuses sign-in from user agents that announce an embedded
                        // WebView ("; wv"); this is the same Chrome engine without the marker.
                        settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (finishing) return
                                val header = cookies.getCookie(Account.ORIGIN)
                                if (!Account.looksSignedIn(header)) return
                                finishing = true
                                scope.launch {
                                    runCatching { Account.completeSignIn(header) }
                                        .onSuccess {
                                            cookies.removeAllCookies(null)
                                            cookies.flush()
                                            onDone()
                                        }
                                        .onFailure {
                                            finishing = false
                                            error = "Signed in, but YouTube didn't answer. Check your connection and try again."
                                        }
                                }
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                    }
                },
            )
            if (finishing || error != null) {
                Column(
                    Modifier.fillMaxSize().background(c.surface).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    if (error == null) {
                        MaterialLoader(color = c.primary, size = 36.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Finishing sign-in…", fontWeight = FontWeight.Bold)
                    } else {
                        Text(error.orEmpty(), textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        PillButton("Close", onCancel)
                    }
                }
            }
        }
    }
}

/** Account button in the headers: the avatar when signed in, a person icon otherwise. */
@Composable
fun AccountAction(onClick: () -> Unit) {
    val profile = Account.profile
    if (profile == null) {
        CircleAction(Icons.Rounded.AccountCircle, "Sign in", onClick)
    } else {
        Box(Modifier.size(44.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
            ChannelAvatar(profile.avatar, profile.name, size = 40)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onSignIn: () -> Unit, onSignedOut: () -> Unit, onDismiss: () -> Unit) {
    TrackBlurPopup()
    val c = MaterialTheme.colorScheme
    val profile = Account.profile
    var detail by remember { mutableStateOf<SettingsDetail?>(null) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    // One sheet for the entire account/settings flow. skipPartiallyExpanded means it opens
    // at full height while remaining a normal draggable sheet that can be pulled down.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = popupContainerColor(c.surfaceContainerLow),
        scrimColor = popupScrimColor(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (detail == null) {
                AccountSettingsHome(
                    profile = profile,
                    onSignIn = onSignIn,
                    onSignedOut = onSignedOut,
                    onDismiss = onDismiss,
                    onOpen = { detail = it },
                    onOpenEqualizer = { showEqualizerSheet = true }
                )
            } else {
                SettingsDetailContent(detail!!, onBack = { detail = null })
            }
        }
    }

    if (showEqualizerSheet) {
        EqualizerSheet(onDismiss = { showEqualizerSheet = false })
    }
}

private enum class SettingsDetail { Appearance, Captions, SponsorBlock, Player, Shorts, PopupEffects, Storage, About }

@Composable
private fun AccountSettingsHome(
    profile: Profile?,
    onSignIn: () -> Unit,
    onSignedOut: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (SettingsDetail) -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (profile == null) {
            Text("Your YouTube, without the ads", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to get your recommendations, subscriptions, history and playlists. " +
                    "Likes, subscribes, Watch later and watch history sync back to your account.\n\n" +
                    "Google treats unofficial apps as against its terms and can suspend accounts that use them. " +
                    "A secondary Google account keeps that risk away from your main one.",
                color = c.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PillButton("Sign in with Google", { onDismiss(); onSignIn() })
        } else {
            ChannelAvatar(profile.avatar, profile.name, size = 72)
            Spacer(Modifier.height(12.dp))
            Text(profile.name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            profile.handle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 14.sp) }
            Spacer(Modifier.height(20.dp))
            PillButton("Sign out", { Account.signOut(); onSignedOut(); onDismiss() }, Icons.AutoMirrored.Rounded.Logout)
        }
        Spacer(Modifier.height(28.dp))
        Text("Settings", modifier = Modifier.fillMaxWidth(), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Spacer(Modifier.height(10.dp))
        SettingsEntry(R.drawable.settings_appearance, "Appearance", "Theme, colours, blur effects and AMOLED") { onOpen(SettingsDetail.Appearance) }
        SettingsEntry(R.drawable.settings_captions, "Captions", "Language, style and translation") { onOpen(SettingsDetail.Captions) }
        SettingsEntry(R.drawable.settings_sponsorblock, "SponsorBlock", "Skip sponsors and other segments") { onOpen(SettingsDetail.SponsorBlock) }
        SettingsEntry(R.drawable.settings_player, "Player controls", "Video control preferences") { onOpen(SettingsDetail.Player) }
        SettingsEntry(Icons.Rounded.Equalizer, "Equalizer", "Custom audio frequencies, presets & bass boost") { onOpenEqualizer() }
        SettingsEntry(R.drawable.settings_shorts, "Shorts", "Shorts feed preferences") { onOpen(SettingsDetail.Shorts) }
        SettingsEntry(R.drawable.settings_effects, "Popup effects", "Frosted popup appearance") { onOpen(SettingsDetail.PopupEffects) }
        SettingsEntry(Icons.Rounded.Storage, "Storage & music cache", "Cached songs, offline audio and storage limits") { onOpen(SettingsDetail.Storage) }
        SettingsEntry(R.drawable.settings_info, "About iTube", "App information, developers and updates") { onOpen(SettingsDetail.About) }
    }
}

@Composable
private fun SettingsEntry(iconVector: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(iconVector, null, tint = c.onSurface, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(summary, color = c.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsEntry(icon: Int, title: String, summary: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = c.onSurface, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(summary, color = c.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsDetailContent(detail: SettingsDetail, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pressable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = c.onSurface)
            }
            Text(detail.title, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            when (detail) {
                SettingsDetail.Appearance -> AppearanceSettings()
                SettingsDetail.Captions -> CaptionSettingsCard()
                SettingsDetail.SponsorBlock -> SponsorBlockSettings()
                SettingsDetail.Player -> PlayerControlsSettings()
                SettingsDetail.Shorts -> ShortsSettings()
                SettingsDetail.PopupEffects -> BlurSettings()
                SettingsDetail.Storage -> StorageSettingsContent()
                SettingsDetail.About -> AboutITubeV2()
            }
        }
    }
}

@Composable
private fun AboutITube() {
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val release = AppUpdate.available
    val checking = AppUpdate.checkingNow
    var showNotes by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(if (c.surface.luminance() < 0.5f) R.drawable.itube_dark else R.drawable.itube),
            contentDescription = "iTube",
            modifier = Modifier.size(88.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("i", color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp)
            Text("Tube", color = c.onSurface, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp)
        }
        Text("Version ${BuildConfig.VERSION_NAME}", color = c.onSurfaceVariant, fontSize = 14.sp)
        Text("Open-source video and music", color = c.onSurfaceVariant, fontSize = 13.sp)

        Spacer(Modifier.height(28.dp))
        Text("Developers", modifier = Modifier.fillMaxWidth(), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Spacer(Modifier.height(8.dp))
        DeveloperCard(
            name = "Darkboy",
            role = "Lead Developer",
            avatarUrl = "https://avatars.githubusercontent.com/u/218248866",
            githubUrl = "https://github.com/d0x-dev",
            profileLabel = "github.com/d0x-dev",
            websiteUrl = "https://darkboy.pro",
            websiteLabel = "darkboy.pro",
            uri = uri,
        )
        Spacer(Modifier.height(10.dp))
        DeveloperCard(
            name = "V E N O M",
            role = "UI/UX Specialist",
            avatarUrl = "https://avatars.githubusercontent.com/u/241423835",
            githubUrl = "https://github.com/drkvenom786",
            profileLabel = "github.com/drkvenom786",
            websiteUrl = "https://drkvenom786.github.io/webpage/",
            websiteLabel = "drkvenom786.github.io",
            uri = uri,
        )

        Spacer(Modifier.height(28.dp))
        Text("Updates", modifier = Modifier.fillMaxWidth(), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Checks the published releases from d0x-dev/iTube.",
            color = c.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        PillButton(
            text = if (checking) "Checking…" else "Check for updates",
            onClick = { if (!checking) AppUpdate.checkNow() },
            icon = Icons.Rounded.SystemUpdateAlt,
        )
        if (checking) {
            Spacer(Modifier.height(12.dp))
            MaterialLoader(color = c.primary, size = 24.dp)
        }
        AppUpdate.lastCheckMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = c.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        release?.let {
            Spacer(Modifier.height(14.dp))
            Text("Version ${it.version} is available on GitHub.", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            PillButton("View release notes", { showNotes = true })
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "GitHub · d0x-dev/iTube",
            color = c.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.pressable { uri.openUri("https://github.com/d0x-dev/iTube") },
        )
    }
    if (showNotes && release != null) {
        UpdateNotesSheet(release, onDismiss = { showNotes = false })
    }
}

@Composable
private fun DeveloperCard(
    name: String,
    role: String,
    avatarUrl: String,
    githubUrl: String,
    profileLabel: String,
    websiteUrl: String,
    websiteLabel: String,
    uri: androidx.compose.ui.platform.UriHandler,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceContainerHigh)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .pressable { uri.openUri(githubUrl) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelAvatar(avatarUrl, name, size = 54)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(role, color = c.onSurfaceVariant, fontSize = 13.sp)
            Text(profileLabel, color = c.primary, fontSize = 12.sp)
            Text(websiteLabel, color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.pressable { uri.openUri(websiteUrl) })
        }
        Icon(painterResource(R.drawable.github), contentDescription = "Open GitHub profile", tint = c.primary, modifier = Modifier.size(22.dp))
    }
}

/** AirBeats-style About layout, kept inside the app's single draggable settings sheet. */
@Composable
private fun AboutITubeV2() {
    val c = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val release = AppUpdate.available
    val installedReleaseNotes = AppUpdate.releaseNotesFor(BuildConfig.VERSION_NAME)
    val checking = AppUpdate.checkingNow
    var showNotes by remember { mutableStateOf(false) }
    var showInstalledNotes by remember { mutableStateOf(false) }
    val spin = rememberInfiniteTransition(label = "updateCheck").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
        label = "updateCheckSpin",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = painterResource(if (c.surface.luminance() < 0.5f) R.drawable.itube_dark else R.drawable.itube),
                contentDescription = "iTube",
                modifier = Modifier.size(80.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("i", color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
            Text("Tube", color = c.onSurface, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
            AboutBadge(BuildConfig.VERSION_NAME)
            AboutBadge(if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")
        }
        Text("Dev By DxV STUDIO", color = c.onSurfaceVariant, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(CircleShape).background(c.surfaceContainerHigh)
                .border(1.dp, c.outlineVariant.copy(alpha = 0.55f), CircleShape)
                .pressable { if (!checking) AppUpdate.checkNow() }
                .padding(start = 15.dp, end = 17.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (checking) Icons.Rounded.Refresh else Icons.Rounded.SystemUpdateAlt,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(19.dp).graphicsLayer { rotationZ = if (checking) spin.value else 0f },
            )
            Text(
                when {
                    checking -> "Checking releases…"
                    release != null -> "Update ${release.version} available"
                    else -> "Check for updates"
                },
                fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
            )
        }
        AppUpdate.lastCheckMessage?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
        release?.let { Text("View release notes", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.pressable { showNotes = true }.padding(top = 9.dp)) }
        Spacer(Modifier.height(12.dp))
        Text("Installed version notes", modifier = Modifier.fillMaxWidth(), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        if (installedReleaseNotes != null) {
            PillButton("View iTube ${installedReleaseNotes.version} notes", { showInstalledNotes = true })
        } else {
            Text(
                "No release notes were published for iTube ${BuildConfig.VERSION_NAME}.",
                modifier = Modifier.fillMaxWidth(),
                color = c.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        AboutSocialHub(uri)
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Founders", fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp)
            Spacer(Modifier.width(9.dp))
            Text("2 Founders", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            FounderCardV2(
                name = "Darkboy", role = "Lead Developer", avatarUrl = "https://avatars.githubusercontent.com/u/218248866",
                githubUrl = "https://github.com/d0x-dev", profileLabel = "github.com/d0x-dev", website = "https://darkboy.pro", uri = uri,
                modifier = Modifier.weight(1f),
            )
            FounderCardV2(
                name = "V E N O M", role = "UI/UX Specialist", avatarUrl = "https://avatars.githubusercontent.com/u/241423835",
                githubUrl = "https://github.com/drkvenom786", profileLabel = "github.com/drkvenom786", website = "https://drkvenom786.github.io/webpage/", uri = uri,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("GitHub · d0x-dev/iTube", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.pressable { uri.openUri("https://github.com/d0x-dev/iTube") })
    }
    if (showNotes && release != null) UpdateNotesSheet(release, onDismiss = { showNotes = false })
    if (showInstalledNotes && installedReleaseNotes != null) {
        InstalledVersionNotesSheet(installedReleaseNotes, onDismiss = { showInstalledNotes = false })
    }
}

@Composable
private fun AboutBadge(text: String) {
    val c = MaterialTheme.colorScheme
    Text(text.uppercase(), color = c.primary, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
        modifier = Modifier.clip(CircleShape).border(1.dp, c.primary.copy(alpha = 0.8f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun AboutSocialHub(uri: androidx.compose.ui.platform.UriHandler) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerHigh)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(28.dp)).padding(vertical = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AboutSocialBubble(R.drawable.facebook, "https://www.facebook.com/venom.digital.creator", uri, "Facebook")
        AboutSocialBubble(R.drawable.instagram, "https://www.instagram.com/Dark__336/", uri, "Instagram")
        AboutSocialBubble(R.drawable.github, "https://github.com/d0x-dev", uri, "GitHub")
        AboutSocialBubble(R.drawable.google, "https://g.dev/Darkboy336", uri, "Google Developer")
        AboutSocialBubble(R.drawable.arrow_outward, "https://darkboy.pro", uri, "Website")
    }
}

@Composable
private fun AboutSocialBubble(
    @androidx.annotation.DrawableRes iconRes: Int,
    url: String,
    uri: androidx.compose.ui.platform.UriHandler,
    contentDescription: String,
) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(1.dp, c.outline.copy(alpha = 0.55f), CircleShape)
            .pressable { uri.openUri(url) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = c.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FounderCardV2(
    name: String,
    role: String,
    avatarUrl: String,
    githubUrl: String,
    profileLabel: String,
    website: String,
    uri: androidx.compose.ui.platform.UriHandler,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.height(276.dp).clip(RoundedCornerShape(28.dp)).background(c.surfaceContainerHigh)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(28.dp))
            .pressable { uri.openUri(githubUrl) }.padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChannelAvatar(avatarUrl, name, size = 86)
        Spacer(Modifier.height(13.dp))
        Text(name, maxLines = 1, fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(role, maxLines = 1, color = c.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.clip(CircleShape).background(c.surfaceVariant).padding(horizontal = 9.dp, vertical = 5.dp))
        Spacer(Modifier.weight(1f))
        Text(profileLabel, color = c.onSurfaceVariant, maxLines = 1, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            AboutSocialBubble(R.drawable.github, githubUrl, uri, "GitHub")
            AboutSocialBubble(R.drawable.arrow_outward, website, uri, "Website")
        }
    }
}

private val SettingsDetail.title: String
    get() = when (this) {
        SettingsDetail.Appearance -> "Appearance"
        SettingsDetail.Captions -> "Captions"
        SettingsDetail.SponsorBlock -> "SponsorBlock"
        SettingsDetail.Player -> "Player controls"
        SettingsDetail.Shorts -> "Shorts"
        SettingsDetail.PopupEffects -> "Popup effects"
        SettingsDetail.Storage -> "Storage & cache"
        SettingsDetail.About -> "About iTube"
    }

@Composable
private fun StorageSettingsContent() {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val totalBytes = MusicCacheManager.totalStorageBytes.longValue
    val cachedCount = MusicCacheManager.cachedTracks.size
    val cachingEnabled = MusicCacheManager.cachingEnabled.value
    val maxLimitMB = MusicCacheManager.maxCacheLimitMB.longValue

    var showLimitDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val limitOptions = listOf(
        500L to "500 MB",
        1024L to "1 GB",
        2048L to "2 GB (Default)",
        5120L to "5 GB",
        10240L to "10 GB",
        -1L to "Unlimited"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Storage Usage Meter Card ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surfaceContainerHigh)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(c.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = c.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = MusicCacheManager.formatStorageSize(totalBytes),
                            fontFamily = RoundedHeavy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = c.onSurface
                        )
                        Text(
                            text = "$cachedCount songs cached offline",
                            fontSize = 13.sp,
                            color = c.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Progress Bar
                if (maxLimitMB > 0) {
                    val maxBytes = maxLimitMB * 1024L * 1024L
                    val fraction = (totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (fraction > 0.85f) c.error else c.primary,
                        trackColor = c.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(fraction * 100).toInt()}% of limit used",
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant
                        )
                        Text(
                            text = "Limit: ${if (maxLimitMB >= 1024) "${maxLimitMB / 1024} GB" else "${maxLimitMB} MB"}",
                            fontSize = 11.sp,
                            color = c.onSurfaceVariant
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { 0.1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = c.primary,
                        trackColor = c.surfaceContainerHighest
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Unlimited storage enabled",
                        fontSize = 11.sp,
                        color = c.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Audio only: Video streams are never cached to protect device memory.",
                    fontSize = 11.sp,
                    color = c.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }

        // ── Toggle: Cache songs while playing ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surfaceContainerLow)
                .pressable { MusicCacheManager.setCachingEnabled(!cachingEnabled) }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cache songs while playing",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = c.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Automatically save songs to offline storage as you listen so they play offline next time",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = cachingEnabled,
                onCheckedChange = { MusicCacheManager.setCachingEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.primary,
                    checkedTrackColor = c.primaryContainer
                )
            )
        }

        // ── Setting: Storage Limit ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surfaceContainerLow)
                .pressable { showLimitDialog = true }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage limit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = c.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (maxLimitMB <= 0) "Unlimited storage" else "Maximum $maxLimitMB MB (auto-evicts oldest songs)",
                    fontSize = 12.sp,
                    color = c.onSurfaceVariant
                )
            }
            Text(
                text = if (maxLimitMB <= 0) "Unlimited" else if (maxLimitMB >= 1024) "${maxLimitMB / 1024} GB" else "$maxLimitMB MB",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = c.primary
            )
        }

        // ── Button: Clear All Cached Music ──
        if (cachedCount > 0) {
            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.errorContainer,
                    contentColor = c.onErrorContainer
                )
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Clear cached songs (${MusicCacheManager.formatStorageSize(totalBytes)})",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Limit Selection Dialog
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = {
                Text("Select storage limit", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    limitOptions.forEach { (mb, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable {
                                    MusicCacheManager.setMaxCacheLimitMB(mb)
                                    showLimitDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = maxLimitMB == mb,
                                onClick = {
                                    MusicCacheManager.setMaxCacheLimitMB(mb)
                                    showLimitDialog = false
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (maxLimitMB == mb) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLimitDialog = false }) { Text("Close") }
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Clear cached songs?", fontFamily = RoundedHeavy, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete all cached songs? This will free up ${MusicCacheManager.formatStorageSize(totalBytes)} of device storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        MusicCacheManager.clearAllCache()
                        showClearDialog = false
                        Toast.makeText(context, "Cached music cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.error,
                        contentColor = c.onError
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}
