package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.YoutubeExtension.Companion.ENGLISH
import dev.brahmkshatriya.echo.extension.YoutubeExtension.Companion.SINGLES
import dev.brahmkshatriya.echo.extension.endpoints.GoogleAccountResponse
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.model.external.ThumbnailProvider
import dev.toastbits.ytmkt.model.external.mediaitem.MediaItemLayout
import dev.toastbits.ytmkt.model.external.mediaitem.YtmArtist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmMediaItem
import dev.toastbits.ytmkt.model.external.mediaitem.YtmPlaylist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmSong
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

suspend fun MediaItemLayout.toMediaItemsContainer(
    api: YoutubeiApi,
    language: String,
    quality: ThumbnailProvider.Quality
): MediaItemsContainer {
    val s = title?.getString(ENGLISH)
    val single = s == SINGLES
    return MediaItemsContainer.Category(
        title = title?.getString(language) ?: "Unknown",
        subtitle = subtitle?.getString(language),
        list = items.mapNotNull { item ->
            item.toEchoMediaItem(api, single, quality)
        },
        more = view_more?.getBrowseParamsData()?.browse_id?.let { id ->
            PagedData.Single {
                val rows =
                    api.GenericFeedViewMorePage.getGenericFeedViewMorePage(id).getOrThrow()
                rows.mapNotNull { itemLayout ->
                    itemLayout.toEchoMediaItem(api, single, quality)
                }
            }
        }
    )
}

fun YtmMediaItem.toEchoMediaItem(
    api: YoutubeiApi,
    single: Boolean,
    quality: ThumbnailProvider.Quality
): EchoMediaItem? {
    val channelId = api.user_auth_state?.own_channel_id
    return when (this) {
        is YtmSong -> EchoMediaItem.TrackItem(toTrack(quality))
        is YtmPlaylist -> when (type) {
            YtmPlaylist.Type.ALBUM -> EchoMediaItem.Lists.AlbumItem(toAlbum(single, quality))
            else -> {
                if (id != "VLSE") EchoMediaItem.Lists.PlaylistItem(toPlaylist(quality))
                else null
            }
        }

        is YtmArtist -> toArtist(quality).let { EchoMediaItem.Profile.ArtistItem(it) }
        else -> null
    }
}

fun YtmPlaylist.toPlaylist(
    quality: ThumbnailProvider.Quality, related: String? = null
): Playlist {
    val extras = mutableMapOf<String, String>()
    related?.let { extras["relatedId"] = it }
    return Playlist(
        id = id,
        title = name ?: "Unknown",
        isEditable = owner_id.toBoolean(),
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        authors = artists?.map { it.toUser(quality) } ?: emptyList(),
        tracks = item_count,
        duration = total_duration,
        creationDate = year?.toString(),
        description = description,
        subtitle = artists?.joinToString(", ") { it.name ?: "Unknown" } ?: year?.toString(),
        extras = extras,
    )
}

fun YtmPlaylist.toAlbum(
    single: Boolean = false,
    quality: ThumbnailProvider.Quality
): Album {
    return Album(
        id = id,
        title = name ?: "Unknown",
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        artists = artists?.map { it.toArtist(quality) } ?: emptyList(),
        tracks = item_count ?: if (single) 1 else null,
        releaseDate = year?.toString(),
        publisher = null,
        duration = total_duration,
        description = description,
        subtitle = year?.toString(),
    )
}

fun YtmSong.toTrack(
    quality: ThumbnailProvider.Quality,
    setId: String? = null
): Track {
    val album = album?.toAlbum(false, quality)
    val extras = mutableMapOf<String, String>()
    related_browse_id?.let { extras["relatedId"] = it }
    lyrics_browse_id?.let { extras["lyricsId"] = it }
    setId?.let { extras["setId"] = it }
    return Track(
        id = id,
        title = name ?: "Unknown",
        artists = artists?.map { it.toArtist(quality) } ?: emptyList(),
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(crop = true)
            ?: getCover(id, quality),
        album = album,
        duration = duration,
        plays = null,
        releaseDate = album?.releaseDate,
        liked = is_explicit,
        extras = extras,
    )
}

private fun getCover(
    id: String,
    quality: ThumbnailProvider.Quality
): ImageHolder.UrlRequestImageHolder {
    return when (quality) {
        ThumbnailProvider.Quality.LOW -> "https://img.youtube.com/vi/$id/mqdefault.jpg"
        ThumbnailProvider.Quality.HIGH -> "https://img.youtube.com/vi/$id/maxresdefault.jpg"
    }.toImageHolder(crop = true)
}

fun YtmArtist.toArtist(
    quality: ThumbnailProvider.Quality,
): Artist {
    return Artist(
        id = id,
        name = name ?: "Unknown",
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        description = description,
        followers = subscriber_count,
    )
}

fun YtmArtist.toUser(
    quality: ThumbnailProvider.Quality,
): User {
    return User(
        id = id,
        name = name ?: "Unknown",
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf())
    )
}

fun User.toArtist(): Artist {
    return Artist(
        id = id,
        name = name,
        cover = cover,
        extras = extras
    )
}


private val jsonParser = Json { ignoreUnknownKeys = true }
suspend fun HttpResponse.getUsers(
    cookie: String,
    auth: String
) = bodyAsText().let {
    val trimmed = it.substringAfter(")]}'")
    jsonParser.decodeFromString<GoogleAccountResponse>(trimmed)
}.getUsers(cookie, auth)
