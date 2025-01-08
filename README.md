# ProxyGuard

![ProxyGuard Logo](https://github.com/user-attachments/assets/173df816-7ee6-4f99-b2dd-8605188e2c02)

ProxyGuard is a lightweight BungeeCord/Waterfall plugin designed to protect premium Minecraft usernames on mixed-mode (online/offline) servers.

## 🛡️ Key Features

- **Premium Username Protection**: Prevents cracked players from using legitimate premium Minecraft account names
- **Simple Management**: Easy-to-use commands for adding, removing, and checking premium usernames
- **Comprehensive Logging**: Detailed logging system tracks unauthorized login attempts
- **Flexible Compatibility**: 
  - Works alongside other authentication plugins
  - No database required
  - Compatible with all BungeeCord versions

## 🎮 Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `/proxyguard add <username>` | Add a premium player | Protect a specific username |
| `/proxyguard remove <username>` | Remove a premium player | Unprotect a username |
| `/proxyguard check <username>` | Check premium status | Verify a username's protection |
| `/proxyguard list` | List protected players | View all premium usernames |

## 🔐 Permissions

| Permission | Description |
|-----------|-------------|
| `proxyguard.admin` | Full access to all ProxyGuard commands |

## 📸 Screenshots

### Plugin Startup
![OnEnable Screenshot](https://github.com/user-attachments/assets/5c886756-4271-4cb4-810b-d5e35adab33a)

### Command Interface
![Commands Screenshot](https://github.com/user-attachments/assets/1740604f-abe8-4d6b-a7da-3b499bbd38c1)

## 🚀 Installation

1. Stop your BungeeCord server
2. Place `ProxyGuard.jar` in the `plugins` folder
3. Start your server
4. Use `/proxyguard add <username>` to protect premium usernames

## 📝 Configuration

### config.yml (Example)
```yaml
# ProxyGuard Configuration

# List of protected premium usernames
protected-usernames:
  - Notch
  - Jeb_
  - Dinnerbone

# Logging configuration
logging:
  enabled: true
  log-file: proxyguard.log
  log-unauthorized-attempts: true

# Kick message for unauthorized login attempts
kick-message: "&cSorry, this username is protected and cannot be used on this server."
```

## 🆘 Support

Need help? Found a bug? Have a suggestion?

- Join our Discord: [ProxyGuard Support](https://discord.gg/hdXRVacpgf)

## 📋 Upcoming Features

- Enhanced logging capabilities
- More granular username protection
- Integration with other security plugins

## 🤝 Contributing

Contributions are welcome! Please open an issue or submit a pull request on our GitHub repository.
