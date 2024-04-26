package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
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

fun MediaItemLayout.toMediaItemsContainer(
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
            continuationFlow { _ ->
                val rows =
                    api.GenericFeedViewMorePage.getGenericFeedViewMorePage(id).getOrThrow()
                val data = rows.mapNotNull { itemLayout ->
                    itemLayout.toEchoMediaItem(api, single, quality)
                }
                Page(data, null)
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
            else -> EchoMediaItem.Lists.PlaylistItem(toPlaylist(channelId, quality))
        }

        is YtmArtist -> toArtist(quality)?.let { EchoMediaItem.Profile.ArtistItem(it) }
        else -> null
    }
}

fun YtmPlaylist.toPlaylist(
    channelId: String?, quality: ThumbnailProvider.Quality, related: String? = null
): Playlist {
    val extras = mutableMapOf<String, String>()
    related?.let { extras["relatedId"] = it }
    item_set_ids?.let { extras["item_set_ids"] = it.joinToString(",") }
    return Playlist(
        id = id,
        title = name ?: "Unknown",
        isEditable = owner_id != null && channelId == owner_id,
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        authors = artists?.mapNotNull { it.toArtist(quality) } ?: emptyList(),
        tracks = items?.map { it.toTrack(quality) } ?: emptyList(),
        subtitle = description?.takeIf { it.isNotEmpty() } ?: year?.toString(),
        duration = total_duration ?: items?.sumOf { it.duration ?: 0 },
        creationDate = year?.toString(),
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
        artists = artists?.mapNotNull {
            it.toArtist(quality)
        } ?: emptyList(),
        numberOfTracks = item_count ?: if (single) 1 else null,
        releaseDate = year?.toString(),
        tracks = items?.map { it.toTrack(quality) } ?: emptyList(),
        publisher = null,
        duration = total_duration,
        description = description,
        subtitle = year?.toString(),
    )
}

fun YtmSong.toTrack(
    quality: ThumbnailProvider.Quality
): Track {
    val album = album?.toAlbum(false, quality)
    val extras = related_browse_id?.let { mapOf("relatedId" to it) }
    return Track(
        id = id,
        title = name ?: "Unknown",
        artists = artists?.mapNotNull { it.toArtist(quality) } ?: emptyList(),
        cover = getCover(id, quality),
        album = album,
        duration = duration,
        plays = null,
        releaseDate = album?.releaseDate,
        liked = false,
        extras = extras ?: emptyMap(),
    )
}

private fun getCover(
    id: String,
    quality: ThumbnailProvider.Quality
): ImageHolder.UrlRequestImageHolder {
    return SongThumbnailProvider(id).getThumbnailUrl(quality).toImageHolder(crop = true)
}

data class SongThumbnailProvider(val id: String) : ThumbnailProvider {
    override fun getThumbnailUrl(quality: ThumbnailProvider.Quality): String =
        when (quality) {
            ThumbnailProvider.Quality.LOW -> "https://img.youtube.com/vi/$id/mqdefault.jpg"
            ThumbnailProvider.Quality.HIGH -> "https://img.youtube.com/vi/$id/maxresdefault.jpg"
        }
}

fun YtmArtist.toArtist(
    quality: ThumbnailProvider.Quality,
): Artist? {
    return Artist(
        id = id,
        name = name ?: return null,
        cover = thumbnail_provider?.getThumbnailUrl(quality)?.toImageHolder(mapOf()),
        description = description,
        followers = subscriber_count,
    )
}


private val jsonParser = Json { ignoreUnknownKeys = true }
suspend fun HttpResponse.getArtists(
    cookie: String,
    auth: String
) = bodyAsText().let {
    val trimmed = it.substringAfter(")]}'")
    jsonParser.decodeFromString<GoogleAccountResponse>(trimmed)
}.getArtists(cookie, auth)
