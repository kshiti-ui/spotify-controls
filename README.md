# Spotify Controls Mod for Minecraft 1.21

Control your Spotify playback directly from Minecraft using simple commands!

## Features

- 🎵 **Full Playback Control**: Play, pause, skip, previous track
- 🔊 **Volume Control**: Set volume from 0-100%
- 🔁 **Loop Modes**: Track, context, or off
- 📢 **Now Playing Toasts**: Automatic notifications when songs change
- 🔐 **Secure Authentication**: OAuth2 login flow
- 💾 **Persistent Storage**: Tokens saved locally

## Setup Instructions

### 1. Create Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Log in with your Spotify account
3. Click "Create app"
4. Fill in the details:
   - **App name**: Minecraft Spotify Controls
   - **App description**: Control Spotify from Minecraft
   - **Redirect URI**: `http://127.0.0.1:8888/callback`
   - **API/SDKs**: Select Web API
5. Click "Save"
6. Note down your **Client ID** and **Client Secret**

### 2. Configure the Mod

1. Open `src/main/java/com/example/spotifycontrols/spotify/SpotifyAuth.java`
2. Replace the following lines:
   ```java
   private static final String CLIENT_ID = "YOUR_CLIENT_ID_HERE";
   private static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET_HERE";
   ```
   With your actual credentials from Spotify Developer Dashboard

### 3. Build the Mod

```bash
# Make sure you have Java 21+ installed
./gradlew build

# The compiled mod will be in:
# build/libs/spotifycontrols-1.0.0.jar
```

### 4. Install the Mod

1. Copy `build/libs/spotifycontrols-1.0.0.jar` to your Minecraft `.minecraft/mods` folder
2. Make sure you have Fabric Loader and Fabric API installed
3. Launch Minecraft 1.21

## Commands

### Authentication
```
/spotify login    - Opens browser for Spotify login
/spotify logout   - Logs out and clears tokens
/spotify status   - Check connection status
```

### Playback Control
```
/spotify resume    - Resume playback
/spotify pause     - Pause playback
/spotify skip      - Skip to next track
/spotify previous  - Go to previous track
/spotify current   - Show currently playing track
/spotify play <song name> - Search and play a song
```

### Settings
```
/spotify volume <0-100>           - Set volume (e.g., /spotify volume 50)
/spotify loop <track|context|off> - Set repeat mode
  - track: Repeat current track
  - context: Repeat playlist/album
  - off: No repeat
```

## Usage Example

```
# First time setup
/spotify login
[Browser opens or click the link in chat to log into Spotify]
[Return to Minecraft]

# Control your music
/spotify resume
/spotify volume 75
/spotify play never gonna give you up
/spotify skip
/spotify loop track
/spotify pause
```

## Features in Detail

### Automatic Song Change Notifications
- When a song changes, you'll see an **advancement toast notification** (top-right corner)
- Format: "♪ Now Playing" with song name and artist below
- Checks every 3 seconds (configurable in code)
- Uses the same notification system as achievements

### Token Management
- Tokens are stored in `config/spotifycontrols/spotify.json`
- Automatically refreshes expired tokens
- Secure OAuth2 authentication flow

### Error Handling
- Clear error messages for common issues
- Automatic token refresh on expiration
- Graceful handling of Spotify API errors

## Project Structure

```
spotifycontrols/
├── src/main/java/com/example/spotifycontrols/
│   ├── SpotifyControlsMod.java      # Main mod class
│   ├── command/
│   │   └── SpotifyCommand.java      # Command handler
│   └── spotify/
│       ├── SpotifyAuth.java         # OAuth authentication
│       ├── SpotifyAPI.java          # Spotify API wrapper
│       └── TokenStorage.java        # Token persistence
├── src/main/resources/
│   └── fabric.mod.json              # Mod metadata
├── build.gradle                     # Build configuration
├── gradle.properties                # Gradle properties
└── settings.gradle                  # Gradle settings
```

## Requirements

- Minecraft 1.21
- Fabric Loader 0.16.5+
- Fabric API 0.100.8+
- Java 21+
- Active Spotify Premium account (required for playback control API)

## Troubleshooting

### "Not logged in" error
- Run `/spotify login` and complete the authentication in your browser
- Make sure the redirect URI in Spotify Dashboard matches: `http://localhost:8888/callback`

### "Failed to play/pause" error
- Check that Spotify is open on at least one device
- Verify you have Spotify Premium (free accounts can't use playback API)
- Try `/spotify status` to check connection

### Token expired
- The mod automatically refreshes tokens
- If it fails, try `/spotify logout` then `/spotify login` again

### Browser doesn't open
- Manually open the URL shown in chat/logs
- Check if port 8888 is available

### Toast notifications not appearing
- Ensure you're playing music on Spotify
- Check that the mod is properly loaded (check `/spotify status`)

## API Rate Limits

- Spotify has rate limits on their API
- The mod checks for song changes every 3 seconds to avoid hitting limits
- Commands have no artificial delay

## Privacy & Security

- Tokens are stored locally in your Minecraft config folder
- No data is sent anywhere except to Spotify's official API
- Tokens can be cleared anytime with `/spotify logout`

## Development

### Building from Source
```bash
git clone <your-repo-url>
cd spotifycontrols
./gradlew build
```

### Testing
1. Update `SpotifyAuth.java` with your credentials
2. Run `./gradlew runClient`
3. Test commands in-game

### Customization

**Change song check interval** (SpotifyControlsMod.java):
```java
private static final int CHECK_INTERVAL = 60; // 60 ticks = 3 seconds
```

**Add more scopes** (SpotifyAuth.java):
```java
private static final String SCOPES = String.join(" ", 
    "user-read-playback-state",
    "user-modify-playback-state",
    "user-read-currently-playing",
    "playlist-read-private" // Add more as needed
);
```

## Credits

- Built with [Fabric](https://fabricmc.net/)
- Uses [Spotify Web API](https://developer.spotify.com/documentation/web-api)

## License

MIT License - Feel free to modify and redistribute

## Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review Minecraft logs in `.minecraft/logs/latest.log`
3. Ensure Spotify credentials are correctly configured
4. Verify Spotify Premium subscription is active

## Contributing

Contributions are welcome! Feel free to:
- Report bugs
- Suggest features
- Submit pull requests

---

**Note**: This mod requires Spotify Premium as the Spotify Web API only allows playback control for Premium accounts.
