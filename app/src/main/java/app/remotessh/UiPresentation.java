package app.remotessh;

/** Explicit adapters for legacy app-owned presentation state. Never call on remote payloads. */
final class UiPresentation {
  static String status(String value) {
    switch (value) {
      case "请先连接服务器": return UiText.get(R.string.ui_connect_before_attachment);
      case "正在接续或提交消息，请稍候再添加附件": return UiText.get(R.string.ui_wait_before_attachment);
      case "附件正在上传，请稍候": return UiText.get(R.string.ui_the_attachment_is_being_uploaded_please_wait);
      case "未连接": return UiText.get(R.string.ui_not_connected);
      case "已连接": return UiText.get(R.string.ui_connected_419);
      case "正在连接…": return UiText.get(R.string.ui_connecting_420);
      case "正在停止…": return UiText.get(R.string.ui_stopping_421);
      case "已停止": return UiText.get(R.string.ui_stopped);
      case "正在提交回答…": return UiText.get(R.string.ui_submitting_answer);
      case "线程已释放，请接续": return UiText.get(R.string.ui_conversation_released_resume_to_continue);
      case "历史自动刷新失败，可手动重试": return UiText.get(R.string.ui_automatic_history_refresh_failed_retry_manually);
      case "正在停止远端任务并重启 Codex…": return UiText.get(R.string.ui_stopping_remote_tasks_and_restarting_codex);
      case "正在重启远端 Codex…": return UiText.get(R.string.ui_restarting_remote_codex);
      case "正在接续对话…": return UiText.get(R.string.ui_resuming_conversation);
      case "正在同步离线消息…": return UiText.get(R.string.ui_synchronizing_offline_messages);
      case "正在获取对话历史…": return UiText.get(R.string.ui_loading_conversation_history);
      case "正在恢复其他对话…": return UiText.get(R.string.ui_restoring_other_conversations);
      case "正在检查对话状态…": return UiText.get(R.string.ui_checking_conversation_status);
      case "正在刷新对话历史…": return UiText.get(R.string.ui_refreshing_conversation_history);
      case "正在思考…": return UiText.get(R.string.ui_thinking_439);
      case "正在处理…": return UiText.get(R.string.ui_processing_14);
      case "准备远端持久会话（需要 Python 3）…": return UiText.get(R.string.ui_preparing_persistent_remote_session_requires_python_3);
      case "连接主机（TCP）…": return UiText.get(R.string.ui_connecting_to_host_tcp);
      case "检查远端环境（SSH 已认证）…": return UiText.get(R.string.ui_checking_remote_environment_ssh_authenticated);
      case "SSH 握手与认证…": return UiText.get(R.string.ui_ssh_handshake_and_authentication);
      case "检查 SFTP 与家目录…": return UiText.get(R.string.ui_checking_sftp_and_home_directory);
      case "启动远端程序…": return UiText.get(R.string.ui_starting_remote_program);
      case "远端协议握手 / 通信…": return UiText.get(R.string.ui_remote_protocol_handshake_communication);
      default: return value;
    }
  }
  static String profileValidation(IllegalArgumentException error) {
    switch (error.getMessage() == null ? "" : error.getMessage()) {
      case "请输入有效的 SSH 主机地址，不包含 ssh://": return UiText.get(R.string.ui_invalid_host);
      case "请输入 SSH 用户名": return UiText.get(R.string.ui_missing_username);
      case "端口必须在 1–65535 之间": return UiText.get(R.string.ui_port_out_of_range);
      case "请输入远端启动命令": return UiText.get(R.string.ui_missing_startup_command);
      case "项目目录需要填写远端绝对路径，例如 /home/me/project": return UiText.get(R.string.ui_absolute_project_path_required);
      default: return error.getMessage();
    }
  }
  static String permission(PermissionMode mode) {
    switch (mode) {
      case INHERIT: return UiText.get(R.string.ui_use_remote_permissions);
      case DEFAULT: return UiText.get(R.string.ui_ask_for_approval);
      case READ_ONLY: return UiText.get(R.string.ui_read_only);
      case FULL: return UiText.get(R.string.ui_full_access);
      default: throw new AssertionError(mode);
    }
  }
  static String itemTitle(ChatItem item) {
    switch (item.type) {
      case "reasoning": return UiText.get(R.string.ui_reasoning);
      case "fileChange": return UiText.get(R.string.ui_file_changes_430);
      case "imageView": return UiText.get(R.string.ui_view_image);
      case "imageGeneration": return UiText.get(R.string.ui_generate_image);
      case "webSearch": return item.text.startsWith("搜索 · ") ? UiText.get(R.string.ui_search) + item.text.substring(5) : item.text;
      default: return item.text;
    }
  }
  static String modelStatus(String value) {
    switch (value) {
      case "正在刷新模型…": return UiText.get(R.string.ui_refreshing_models);
      case "远端未返回可选模型，可使用远端默认": return UiText.get(R.string.ui_no_model_options_were_returned_you_can_use_the_remote_default);
      case "已同步远端模型": return UiText.get(R.string.ui_remote_models_synchronized);
      default: return prefix(value, "刷新失败，保留上次列表：", R.string.ui_refresh_failed_keeping_the_previous_list);
    }
  }
  static String usageStatus(String value) {
    switch (value) {
      case "连接后查看剩余用量": return UiText.get(R.string.ui_connect_to_view_remaining_usage);
      case "远端未提供用量数据": return UiText.get(R.string.ui_no_usage_data_provided);
      default: return prefix(value, "用量暂不可用：", R.string.ui_usage_temporarily_unavailable);
    }
  }
  private static String prefix(String value, String prefix, int resource) {
    return value.startsWith(prefix) ? UiText.get(resource) + value.substring(prefix.length()) : value;
  }
}
