# Shannon Security Audit & Production Checklist

## 🔒 Security Audit - Critical Items

### 1. Cryptographic Key Storage
- [ ] **Android Keystore Integration**
  ```kotlin
  // Production implementation for secure key storage
  class SecureKeyStorage(private val context: Context) {
      fun generateAndStoreKey(alias: String): SecretKey {
          val keyGenerator = KeyGenerator.getInstance(
              KeyProperties.KEY_ALGORITHM_AES,
              "AndroidKeyStore"
          )
          
          val keyGenSpec = KeyGenParameterSpec.Builder(alias)
              .setKeySize(256)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setUserAuthenticationRequired(false)
              .build()
              
          keyGenerator.init(keyGenSpec)
          return keyGenerator.generateKey()
      }
  }
  ```

- [ ] **Verify Reticulum Identity Storage**
  - Current: File-based storage (`~/.reticulum/`)
  - Production: Move to Android Keystore or encrypted SharedPreferences
  - Backup: Encrypted cloud backup with user-controlled passphrase

### 2. Network Security
- [ ] **Certificate Pinning**
  ```kotlin
  // Pin Reticulum transport certificates
  val certificatePinner = CertificatePinner.Builder()
      .add("reticulum.example.com", "sha256/AAAAAAAAAA...")
      .build()
  ```

- [ ] **TLS Verification**
  - Ensure all TCP connections use TLS
  - Validate certificates properly
  - Fall back to known transport nodes only

### 3. Code Obfuscation (ProGuard/R8)
- [ ] **Enable R8 in release builds**
  ```gradle
  android {
      buildTypes {
          release {
              minifyEnabled true
              shrinkResources true
              proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
          }
      }
  }
  ```

- [ ] **Test obfuscated build**
  - Verify Reticulum crypto works after shrinking
  - Test SQLDelight serialization
  - Validate Kotlin reflection calls

### 4. Permission Justification
- [ ] **Foreground Service Permission**
  - Prominent disclosure: "Shannon needs background service to receive messages when the app is closed"
  - Settings screen explanation
  - Graceful fallback when denied

- [ ] **Notification Permission (Android 13+)**
  - In-app explanation before requesting
  - Settings screen for enabling later
  - Clear value proposition to user

- [ ] **Bluetooth Permissions**
  - Only request when Bluetooth transport is enabled
  - Provide rationale for BLE connectivity
  - Allow app to function without BLE

## ⚖️ Legal & Compliance

### 1. Google Play Store Policies
- [ ] **Background Usage Disclosure**
  - Complete "Prominent Disclosure" form
  - Explain foreground service purpose
  - Document battery usage and optimization

- [ ] **Encryption Declaration**
  - Declare encryption in Play Store listing
  - Document that app uses end-to-end encryption
  - Provide encryption policy reference

- [ ] **Content Rating**
  - Submit for proper age rating
  - Disclose user-generated content features
  - Document moderation approach (if any)

### 2. Privacy Policy
- [ ] **Comprehensive Privacy Policy**
  - Data collection: Minimal (only contacts and messages)
  - Data storage: Local device only (no cloud servers)
  - Data transmission: End-to-end encrypted via Reticulum
  - Data retention: User-controlled
  - Third-party services: None (no analytics, no tracking)

- [ ] **Open Source Licenses**
  - Reticulum: MIT/BSD (check specific license)
  - SQLDelight: Apache 2.0
  - Koin: MIT
  - Compose Multiplatform: Apache 2.0
  - Include LICENSES file in app

### 3. Export Compliance
- [ ] **Encryption Export Classification**
  - Check if your country requires encryption export licenses
  - ECC/AES may have export restrictions
  - File EAR (US) or equivalent documentation

## 🎨 Onboarding UX

### 1. First-Run Experience
- [ ] **Welcome Screen**
  - Explain Shannon's value proposition (decentralized, private)
  - Highlight what makes it different from Signal/WhatsApp
  - Set expectations about network requirements

- [ ] **Identity Creation**
  - Generate Reticulum identity automatically
  - Show identity hash for user to share
  - Provide QR code generation for easy sharing

- [ ] **Network Connection**
  - Guide user to connect to transport node
  - Provide list of public nodes
  - Allow manual node entry
  - Show connection status indicators

- [ ] **First Contact**
  - Suggest way to find other Shannon users
  - Provide demo contact for testing
  - Explain how to invite friends

### 2. Help & Documentation
- [ ] **In-App Help**
  - Quick start guide
  - Troubleshooting section
  - FAQ for common issues
  - Link to full documentation

- [ ] **Error Messages**
  - User-friendly error explanations
  - Actionable suggestions for fixes
  - Network troubleshooting guide
  - Permission request explanations

## 🚀 Pre-Launch Testing

### 1. Device Compatibility
- [ ] **Test on multiple Android versions**
  - Android 8.0 (API 26) - minimum supported
  - Android 10 (API 29) - baseline target
  - Android 13 (API 33) - notification permissions
  - Android 14 (API 34) - latest features

- [ ] **Test on different device manufacturers**
  - Samsung (aggressive battery optimization)
  - Xiaomi (custom ROM behavior)
  - Google Pixel (stock Android)
  - OnePlus (custom UI quirks)

- [ ] **Low-end device testing**
  - Test on devices with 2GB RAM
  - Verify performance doesn't degrade
  - Ensure service survives memory pressure

### 2. Network Conditions
- [ ] **Various network types**
  - WiFi (stable, high bandwidth)
  - 4G LTE (mobile, variable bandwidth)
  - 3G (slow, high latency)
  - Network switching (WiFi ↔ mobile)

- [ ] **Edge cases**
  - Airplane mode on/off
  - Roaming between networks
  - Network congestion
  - Intermittent connectivity

### 3. Stress Testing
- [ ] **Extended operation**
  - 24-hour continuous operation
  - 100+ message conversations
  - 30+ minute voice calls
  - Multiple concurrent conversations

- [ ] **Resource usage**
  - Monitor battery consumption
  - Track data usage
  - Measure storage growth
  - Profile CPU usage

## 📱 Store Submission

### 1. Play Store Assets
- [ ] **App Listing**
  - Compelling app description (80-char intro, 4000-char full description)
  - Screenshots (phone, 7-inch tablet, 10-inch tablet)
  - Feature graphic (1024x500)
  - App icon (512x512)
  - Promo video (optional but recommended)

- [ ] **Store Listing Content**
  - Title: "Shannon - Decentralized Messenger"
  - Short description: "Secure, private messaging without servers"
  - Full description: Emphasize decentralization, privacy, no tracking
  - Recent changes: "Initial release - decentralized messaging + voice calls"

### 2. Release Management
- [ ] **Version Naming**
  - Use semantic versioning (1.0.0)
  - Document breaking changes
  - Provide upgrade instructions

- [ ] **Rollback Strategy**
  - Keep previous APK for emergency rollback
  - Monitor crash reports after launch
  - Prepare hotfix process

## 🌐 Post-Launch Monitoring

### 1. Analytics & Crash Reporting
- [ ] **Crashlytics integration**
  - Set up Firebase Crashlytics
  - Configure for production builds
  - Test crash reporting

- [ ] **Performance monitoring**
  - Track ANR rates
  - Monitor app startup time
  - Measure message delivery success rate
  - Voice call quality metrics

### 2. User Feedback
- [ ] **Feedback channels**
  - In-app feedback mechanism
  - GitHub issues for technical users
  - Email support for general users

- [ ] **Issue prioritization**
  - Critical: Security vulnerabilities, data loss
  - High: App crashes, message delivery failures
  - Medium: UI improvements, performance
  - Low: Feature requests

## 🔧 Maintenance & Updates

### 1. Update Strategy
- [ ] **Update schedule**
  - Security updates: Immediately
  - Bug fixes: Monthly
  - Features: Quarterly

- [ ] **Testing process**
  - Run full test suite before each release
  - Test on physical devices
  - Beta testing with trusted users

### 2. Long-term Support
- [ ] **Dependency updates**
  - Track Reticulum library updates
  - Update Kotlin/Compose versions
  - Security patch dependencies

- [ ] **Platform changes**
  - Monitor Android OS changes
  - Adapt to new Play Store policies
  - Support new Android versions

---

## ✅ Final Launch Checklist

- [ ] All tests passing (100+ tests)
- [ ] ProGuard/R8 configured and tested
- [ ] Security audit complete
- [ ] Permissions properly handled
- [ ] Privacy policy published
- [ ] Store assets prepared
- [ ] Tested on multiple devices
- [ ] 24-hour background reliability verified
- [ ] Voice calls tested on real devices
- [ ] Database migration tested
- [ ] Notification deep-links working
- [ ] Crash reporting configured
- [ ] Feedback channels established

**You are ready for production launch!** 🚀