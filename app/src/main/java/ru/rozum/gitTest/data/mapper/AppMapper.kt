package ru.rozum.gitTest.data.mapper

import ru.rozum.gitTest.data.network.dto.*
import ru.rozum.gitTest.domain.entity.*
import javax.inject.Inject

class AppMapper @Inject constructor() {

    fun mapUserInfoDtoToEntity(dto: UserInfoDto): UserInfo = UserInfo(login = dto.login)
    fun mapRepoDtoToEntity(dto: RepoDto): Repo = Repo(
        name = dto.name,
        description = dto.description ?: EMPTY_RESULT_DTO,
        language = dto.language ?: EMPTY_RESULT_DTO
    )

    fun mapRepoDetailsToEntity(dto: RepoDetailsDto): RepoDetails = RepoDetails(
        id = dto.id,
        name = dto.name,
        htmlUser = dto.htmlUser,
        license = dto.license ?: EMPTY_RESULT_DTO,
        starts = dto.starts,
        forks = dto.forks,
        watchers = dto.watchers,
        description = dto.description ?: EMPTY_RESULT_DTO,
        language = dto.language ?: EMPTY_RESULT_DTO,
        branch = dto.branch
    )
}

private const val EMPTY_RESULT_DTO = ""