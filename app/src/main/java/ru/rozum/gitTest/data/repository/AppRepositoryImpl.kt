package ru.rozum.gitTest.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import retrofit2.Response
import ru.rozum.gitTest.R
import ru.rozum.gitTest.data.external.ColorJson
import ru.rozum.gitTest.data.local.*
import ru.rozum.gitTest.data.mapper.AppMapper
import ru.rozum.gitTest.data.network.api.*
import ru.rozum.gitTest.data.network.dto.RepoDto
import ru.rozum.gitTest.domain.entity.*
import ru.rozum.gitTest.domain.repository.AppRepository
import ru.rozum.gitTest.exception.ConcreteCodeException
import javax.inject.Inject
import ru.rozum.gitTest.data.repository.LevelException.*
import ru.rozum.gitTest.di.RegexLegalTokenQualifier
import ru.rozum.gitTest.di.RegexParsingImagesFromFolderQualifier

class AppRepositoryImpl @Inject constructor(
    private val apiGitHubService: ApiGitHubService,
    private val rawGitHubService: RawGitHubService,
    private val mapper: AppMapper,
    private val client: LocalPropertiesClient,
    private val dispatcherIO: CoroutineDispatcher,
    @RegexLegalTokenQualifier private val regexLegalToken: Regex,
    @RegexParsingImagesFromFolderQualifier private val regexParsingImagesFromFolder: Regex,
    @ApplicationContext val context: Context
) : AppRepository {

    override suspend fun getToken(): String = client.getToken()

    override suspend fun getRepositories(): List<Repo> = connect(
        response = apiGitHubService.getRepositories(client.getTokenForGitHub()),
        errorMessageException = context.getString(R.string.something_error),
    ) { list ->
        list.map {
            withContext(dispatcherIO) {
                setColorLanguageRGBInRepoDto(it)
                mapper.mapRepoDtoToEntity(it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun setColorLanguageRGBInRepoDto(repoDto: RepoDto) {
        context.resources.openRawResource(R.raw.colors).use { inputStream ->
            Json.decodeFromStream<JsonObject>(inputStream)[repoDto.language]?.let {
                repoDto.colorLanguageRGB = Json.decodeFromJsonElement<ColorJson>(it).color
            }
        }
    }

    override suspend fun getRepository(repoId: String): RepoDetails = connect(
        response = apiGitHubService.getRepository(client.getTokenForGitHub(), repoId)
    ) { mapper.mapRepoDetailsToEntity(it) }

    override suspend fun signIn(token: String): UserInfo {
        val legalToken = "bearer $token"
        regexLegalToken.also {
            if (!legalToken.matches(it))
                throw IllegalArgumentException(context.getString(R.string.invalid_token))
        }

        return connect(
            response = apiGitHubService.getUserInfo(legalToken),
            errorMessageException = context.getString(R.string.info_for_dev),
            levelErrorMessage = MESSAGE_CODE
        ) {
            client.saveToken(KeyValueStorage(token))
            mapper.mapUserInfoDtoToEntity(it)
        }
    }

    override suspend fun getRepositoryReadme(
        ownerName: String,
        repositoryName: String,
        branchName: String
    ): String = connect(
        response = rawGitHubService.getRepositoryReadme(ownerName, repositoryName, branchName),
        levelErrorMessage = CODE
    ) { readme ->
        val builder = StringBuilder(readme)
        regexParsingImagesFromFolder
            .findAll(builder)
            .map { it.value }
            .forEach { foundLinkImage ->
                addURLForLinkImage(builder, foundLinkImage, ownerName, repositoryName, branchName)
            }
        builder.toString()
    }

    private fun addURLForLinkImage(
        builder: StringBuilder,
        foundLinkImage: String,
        ownerName: String,
        repositoryName: String,
        branchName: String
    ) {
        builder.toString().replace(
            foundLinkImage,
            URI_RAW_GITHUB_WITH_FORMATTERS.format(
                ownerName,
                repositoryName,
                branchName,
                foundLinkImage
            )
        ).also { result ->
            builder.setRange(0, result.length, result)
        }
    }

    private inline fun <T, V> connect(
        response: Response<T>,
        errorMessageException: String = context.getString(R.string.connection_error),
        levelErrorMessage: LevelException = MESSAGE,
        result: (T) -> V
    ): V {
        if (response.isSuccessful) return result.invoke(response.body()!!)
        when (levelErrorMessage) {
            MESSAGE -> throw RuntimeException(errorMessageException)
            CODE -> throw ConcreteCodeException("${response.code()}")
            MESSAGE_CODE -> {
                throw RuntimeException("Code: ${response.code()}\n$errorMessageException")
            }
        }
    }

    private companion object {
        const val URI_RAW_GITHUB_WITH_FORMATTERS = "https://raw.githubusercontent.com/%s/%s/%s/%s"
    }
}

private enum class LevelException {
    MESSAGE, MESSAGE_CODE, CODE
}

