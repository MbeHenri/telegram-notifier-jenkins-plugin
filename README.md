# Telegram Notifier Jenkins Plugin

A Jenkins plugin that sends Telegram notifications after freestyle job execution.

## Features

- Send Telegram notifications based on build results (Success, Failure, Unstable, Aborted, Not Built)
- Secure credential storage using Jenkins Credentials API
- Customizable message templates with build variables
- Markdown formatting support
- Status emojis for quick visual feedback
- Thread-safe for parallel builds

## Requirements

- Jenkins 2.387.3 or later
- Java 11 or later

## Installation

### From Source

1. Clone the repository:

   ```bash
   git clone https://github.com/MbeHenri/telegram-notifier-jenkins-plugin.git
   cd telegram-notifier-jenkins-plugin
   ```

2. Build the plugin:

   ```bash
   mvn clean package
   ```

3. Install the plugin:
   - Go to Jenkins → Manage Jenkins → Manage Plugins → Advanced
   - Upload `target/telegram-notifier.hpi`
   - Restart Jenkins

## Setup

### 1. Create a Telegram Bot

1. Open Telegram and search for `@BotFather`
2. Send the command `/newbot`
3. Follow the instructions to create your bot
4. Copy the bot token provided by BotFather (format: `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)

### 2. Get Your Chat ID

1. Start a conversation with your bot
2. Send any message to the bot
3. Visit: `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
4. Find the `"chat":{"id":...` value in the response (e.g., `987654321`)

### 3. Create Jenkins Credentials

1. Go to Jenkins → Manage Jenkins → Manage Credentials
2. Select a credential domain (e.g., Global)
3. Click "Add Credentials"
4. Select "Secret text" as the kind
5. Enter your bot token in the "Secret" field
6. Give it an ID (e.g., "telegram-bot-token") and description
7. Repeat steps 3-6 for the chat ID

### 4. Configure Your Job

1. Open your freestyle job configuration
2. Scroll to "Post-build Actions"
3. Click "Add post-build action"
4. Select "Telegram Notifier"
5. Select your bot token credential from the dropdown
6. Select your chat ID credential from the dropdown
7. Choose which build results should trigger notifications:
   - Notify on Success
   - Notify on Failure (enabled by default)
   - Notify on Unstable (enabled by default)
   - Notify on Aborted
   - Notify on Not Built
8. Optionally add a custom message template
9. Save your configuration

## Custom Messages

You can customize notification messages using variables:

- `${BUILD_STATUS}` - Build result (SUCCESS, FAILURE, etc.)
- `${JOB_NAME}` - Job name
- `${BUILD_NUMBER}` - Build number
- `${BUILD_DURATION}` - Build duration (e.g., "1m 5s")
- `${BUILD_URL}` - Build URL
- `${CAUSE}` - Build trigger cause

### Example Custom Message

```md
Build ${BUILD_NUMBER} of ${JOB_NAME} finished with status ${BUILD_STATUS}.
Duration: ${BUILD_DURATION}
Triggered by: ${CAUSE}
```

## Default Message Format

```md
[emoji] Build [STATUS]

Job: [JOB_NAME]
Build: #[BUILD_NUMBER]
Duration: [DURATION]

Started by: [CAUSE]

[Custom message if provided]

View build: [BUILD_URL]
```

## Status Emojis

- ✅ Success
- ❌ Failure
- ⚠️ Unstable
- 🛑 Aborted
- ⏸️ Not Built

## Development

### Build Commands

```bash
# Compile
mvn clean compile

# Run tests
mvn clean test

# Run specific test
mvn test -Dtest=TelegramNotifierTest

# Package plugin
mvn clean package

# Test locally (starts Jenkins at http://localhost:8080/jenkins)
mvn hpi:run
```

### Project Structure

```rschap
src/
├── main/
│   ├── java/io/github/mbehenri/jenkins/telegramnotifier/
│   │   ├── TelegramNotifier.java      # Main notifier class
│   │   ├── TelegramSender.java        # HTTP communication
│   │   ├── MessageFormatter.java      # Message formatting
│   │   ├── NotificationTrigger.java   # Trigger enum
│   │   └── config/
│   │       └── TelegramConfig.java    # Configuration constants
│   └── resources/io/github/mbehenri/jenkins/telegramnotifier/
│       ├── TelegramNotifier/
│       │   ├── config.jelly           # UI configuration
│       │   └── help.html              # Setup instructions
│       └── Messages.properties        # i18n strings
└── test/
    └── java/io/github/mbehenri/jenkins/telegramnotifier/
        ├── TelegramNotifierTest.java
        ├── TelegramSenderTest.java
        ├── MessageFormatterTest.java
        └── NotificationTriggerTest.java
```

## Security

- Bot token and chat ID are stored securely using Jenkins Credentials API
- Credentials are encrypted at rest
- HTTPS only (no HTTP fallback)
- No sensitive data is logged

## Troubleshooting

| Issue                      | Possible Cause         | Solution                               |
| -------------------------- | ---------------------- | -------------------------------------- |
| Empty credentials dropdown | No credentials created | Create StringCredentials in Jenkins    |
| No notification sent       | Triggers not selected  | Check trigger checkboxes in job config |
| 401 Unauthorized           | Invalid bot token      | Verify token from @BotFather           |
| 400 Bad Request            | Invalid chat ID        | Get correct chat ID via getUpdates API |
| Timeout                    | Network issues         | Check Jenkins server connectivity      |

## License

This project is licensed under the GNU License - see the [LICENSE](LICENSE) file for details.
