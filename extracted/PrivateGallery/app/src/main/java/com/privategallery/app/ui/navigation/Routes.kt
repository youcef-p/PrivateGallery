package com.privategallery.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val HOME = "home"
    const val FOLDER_DETAIL = "folder/{folderId}"
    const val MEDIA_VIEWER = "folder/{folderId}/media/{mediaId}"
    const val INVITE_USER = "folder/{folderId}/invite"
    const val FOLDER_SETTINGS = "folder/{folderId}/settings"
    const val APP_SETTINGS = "settings"
    const val SECURITY_SETTINGS = "settings/security"
    const val SYNC_STATUS = "folder/{folderId}/sync_status"

    fun folderDetail(folderId: String) = "folder/$folderId"
    fun mediaViewer(folderId: String, mediaId: String) = "folder/$folderId/media/$mediaId"
    fun inviteUser(folderId: String) = "folder/$folderId/invite"
    fun folderSettings(folderId: String) = "folder/$folderId/settings"
    fun syncStatus(folderId: String) = "folder/$folderId/sync_status"
}
