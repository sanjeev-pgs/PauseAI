import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const PauseAIApp());
}

class PauseAIApp extends StatefulWidget {
  const PauseAIApp({super.key});

  @override
  State<PauseAIApp> createState() => _PauseAIAppState();
}

class _PauseAIAppState extends State<PauseAIApp> {
  ThemeMode themeMode = ThemeMode.light;

  @override
  void initState() {
    super.initState();
    _loadTheme();
  }

  Future<void> _loadTheme() async {
    final prefs = await SharedPreferences.getInstance();
    final dark = prefs.getBool('darkMode') ?? false;

    if (mounted) {
      setState(() {
        themeMode =
            dark ? ThemeMode.dark : ThemeMode.light;
      });
    }
  }

  Future<void> _setTheme(bool dark) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('darkMode', dark);

    if (mounted) {
      setState(() {
        themeMode =
            dark ? ThemeMode.dark : ThemeMode.light;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PauseAI',
      themeMode: themeMode,
      theme: ThemeData(
        brightness: Brightness.light,
        useMaterial3: true,
        colorSchemeSeed: Colors.blue,
        scaffoldBackgroundColor: const Color(0xFFF5F7FB),
        cardTheme: CardThemeData(
          elevation: 1,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
      ),
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        useMaterial3: true,
        colorSchemeSeed: Colors.blue,
        cardTheme: CardThemeData(
          elevation: 1,
          margin: EdgeInsets.zero,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: false,
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
      ),
      home: HomeScreen(
        onThemeChanged: _setTheme,
        darkMode: themeMode == ThemeMode.dark,
      ),
    );
  }
}

class HomeScreen extends StatefulWidget {
  final Future<void> Function(bool) onThemeChanged;
  final bool darkMode;

  const HomeScreen({
    super.key,
    required this.onThemeChanged,
    required this.darkMode,
  });

  @override
  State<HomeScreen> createState() =>
      _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const MethodChannel _channel =
      MethodChannel('pauseai/notifications');

  Timer? _timer;

  bool protectionEnabled = true;
  bool linkProtectionEnabled = true;
  bool paymentProtectionEnabled = true;

  String latestApp = '';
  String latestTitle = '';
  String latestMessage = '';

  String aiResult = 'Waiting for notification';
  double aiConfidence = 0;
  double riskScore = 0;
  String riskLevel = 'safe';
  String category = 'General Message';
  List<String> reasons = [];
  String recommendation = '';
  bool aiAnalysing = false;

  bool highRiskDetected = false;

  String detectedUrl = '';
  String urlResult = 'No URL detected';
  int urlRiskScore = 0;

  bool paymentVerification = false;

  bool virusTotalReady = false;
  int virusTotalMalicious = 0;
  int virusTotalSuspicious = 0;

  final Set<String> analyzedNotifications = {};
  bool _checkingNotification = false;
  bool _notificationBaselineReady = false;
  String? _activeNotificationId;

  final Map<String, String> appNames = {
    'com.whatsapp': 'WhatsApp',
    'com.instagram.android': 'Instagram',
    'org.telegram.messenger': 'Telegram',
    'com.microsoft.teams': 'Microsoft Teams',
    'com.google.android.apps.messaging': 'Messages',
    'com.android.messaging': 'Messages',
  };

  @override
  void initState() {
    super.initState();
    _loadSettings();
    _startNotificationPolling();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    final prefs =
        await SharedPreferences.getInstance();

    if (!mounted) return;

    setState(() {
      protectionEnabled =
          prefs.getBool('protectionEnabled') ??
              true;

      linkProtectionEnabled =
          prefs.getBool(
                'linkProtectionEnabled',
              ) ??
              true;

      paymentProtectionEnabled =
          prefs.getBool(
                'paymentProtectionEnabled',
              ) ??
              true;
    });
  }

  Future<void> _saveSetting(
    String key,
    bool value,
  ) async {
    final prefs =
        await SharedPreferences.getInstance();

    await prefs.setBool(key, value);
  }

  void _startNotificationPolling() {
    _checkNotifications();

    _timer = Timer.periodic(
      const Duration(seconds: 2),
      (_) => _checkNotifications(),
    );
  }

  String _getAppName(String packageName) {
    return appNames[packageName] ?? packageName;
  }

  Future<void> _checkNotifications() async {
    if (_checkingNotification ||
        !protectionEnabled) {
      return;
    }

    if (_activeNotificationId != null) {
      return;
    }

    _checkingNotification = true;

    try {
      final result =
          await _channel.invokeMethod(
        'getLatestNotification',
      );

      if (result == null) {
        return;
      }

      final Map<String, dynamic> data;

      if (result is String) {
        final decoded = jsonDecode(result);

        if (decoded is! Map) {
          throw Exception(
            'getLatestNotification returned invalid JSON',
          );
        }

        data =
            Map<String, dynamic>.from(decoded);
      } else if (result is Map) {
        data =
            Map<String, dynamic>.from(result);
      } else {
        throw Exception(
          'getLatestNotification returned ${result.runtimeType}',
        );
      }

      final rawNotifications =
          data['allNotifications'];

      if (rawNotifications == null) {
        return;
      }

      final List<dynamic> notifications;

      if (rawNotifications is String) {
        final decoded =
            jsonDecode(rawNotifications);

        if (decoded is! List) {
          throw Exception(
            'allNotifications returned invalid JSON',
          );
        }

        notifications = decoded;
      } else if (rawNotifications is List) {
        notifications = rawNotifications;
      } else {
        throw Exception(
          'allNotifications returned ${rawNotifications.runtimeType}',
        );
      }

      if (notifications.isEmpty) {
        return;
      }

      final List<Map<String, dynamic>>
          items = [];

      for (final item in notifications) {
        if (item is Map) {
          items.add(
            Map<String, dynamic>.from(item),
          );
        }
      }

      items.sort((a, b) {
        final aTime =
            int.tryParse(
                  '${a['timestamp'] ?? 0}',
                ) ??
                0;

        final bTime =
            int.tryParse(
                  '${b['timestamp'] ?? 0}',
                ) ??
                0;

        return bTime.compareTo(aTime);
      });

      if (!_notificationBaselineReady) {
        for (final item in items) {
          final id =
              '${item['id'] ?? ''}';

          if (id.isNotEmpty) {
            analyzedNotifications.add(id);
          }
        }

        _notificationBaselineReady = true;

        debugPrint(
          'PAUSEAI NOTIFICATION BASELINE READY: ${items.length} existing notifications ignored',
        );

        return;
      }

      Map<String, dynamic>? selected;

      for (final item in items) {
        final id =
            '${item['id'] ?? ''}';

        if (id.isNotEmpty &&
            !analyzedNotifications.contains(id)) {
          selected = item;
          break;
        }
      }

      if (selected == null) {
        return;
      }

      final selectedId =
          '${selected['id'] ?? ''}';

      if (selectedId.isEmpty) {
        return;
      }

      _activeNotificationId =
          selectedId;

      final packageName =
          '${selected['packageName'] ?? selected['package'] ?? ''}';

      final title =
          '${selected['title'] ?? ''}';

      final message =
          '${selected['message'] ?? ''}';

      if (mounted) {
        setState(() {
          latestApp =
              _getAppName(packageName);

          latestTitle = title;
          latestMessage = message;

          aiResult = 'AI analysing...';
          aiConfidence = 0;
          riskScore = 0;
          riskLevel = 'analysing';
          category = 'Analysing';
          reasons = [];
          recommendation = '';

          aiAnalysing = true;
          highRiskDetected = false;

          detectedUrl = '';
          urlResult = 'No URL detected';
          urlRiskScore = 0;

          paymentVerification = false;

          virusTotalReady = false;
          virusTotalMalicious = 0;
          virusTotalSuspicious = 0;
        });
      }

      await _waitForNativeAnalysis(
        selectedId,
        packageName,
      );
    } catch (e, stackTrace) {
      debugPrint(
        'PAUSEAI CHECK ERROR: $e',
      );

      debugPrint(
        'PAUSEAI CHECK STACK: $stackTrace',
      );

      if (mounted) {
        setState(() {
          aiAnalysing = false;
          aiResult = 'AI analysis error';
          aiConfidence = 0;
          riskScore = 0;
          riskLevel = 'error';
        });
      }

      _activeNotificationId = null;
    } finally {
      _checkingNotification = false;
    }
  }

  Future<void> _waitForNativeAnalysis(
    String notificationId,
    String packageName,
  ) async {
    try {
      debugPrint(
        'PAUSEAI WAITING FOR NATIVE AI: $notificationId',
      );

      Map<String, dynamic>? analysis;

      for (int i = 0; i < 30; i++) {
        if (!mounted) {
          return;
        }

        await Future.delayed(
          const Duration(milliseconds: 500),
        );

        final result =
            await _channel.invokeMethod(
          'getNotificationAnalysis',
          {
            'id': notificationId,
          },
        );

        if (result == null) {
          continue;
        }

        final Map<String, dynamic> data;

        if (result is String) {
          final decoded =
              jsonDecode(result);

          if (decoded is! Map) {
            continue;
          }

          data =
              Map<String, dynamic>.from(
            decoded,
          );
        } else if (result is Map) {
          data =
              Map<String, dynamic>.from(
            result,
          );
        } else {
          continue;
        }

        if (data['found'] != true) {
          continue;
        }

        if (data['aiReady'] == true) {
          analysis = data;
          break;
        }
      }

      if (analysis == null) {
        debugPrint(
          'PAUSEAI NATIVE AI TIMEOUT: $notificationId',
        );

        if (mounted) {
          setState(() {
            aiAnalysing = false;
            aiResult = 'AI analysis error';
            aiConfidence = 0;
            riskScore = 0;
            riskLevel = 'error';
          });
        }

        _activeNotificationId = null;
        return;
      }

      final aiScam =
          analysis['aiScam'] == true;

      final probability =
          double.tryParse(
                '${analysis['aiProbability'] ?? 0}',
              ) ??
              0;

      final nativeRiskScore =
          double.tryParse(
                '${analysis['aiRiskScore'] ?? 0}',
              ) ??
              0;

      final nativeRiskLevel =
          '${analysis['riskLevel'] ?? ''}';

      final nativeCategory =
          '${analysis['category'] ?? 'General Message'}';

      final nativeRecommendation =
          '${analysis['recommendation'] ?? ''}';

      final nativeLabel =
          '${analysis['aiLabel'] ?? ''}';

      final nativeResult =
          '${analysis['aiResult'] ?? ''}';

      final highRisk =
          analysis['highRisk'] == true;

      final nativeUrlScore =
          double.tryParse(
                '${analysis['urlRiskScore'] ?? 0}',
              ) ??
              0;

      final nativePayment =
          analysis['paymentVerification'] ==
              true;

      final nativeVirusTotalReady =
          analysis['virusTotalReady'] == true;

      final nativeVirusTotalMalicious =
          int.tryParse(
                '${analysis['virusTotalMalicious'] ?? 0}',
              ) ??
              0;

      final nativeVirusTotalSuspicious =
          int.tryParse(
                '${analysis['virusTotalSuspicious'] ?? 0}',
              ) ??
              0;

      final List<String> nativeReasons = [];

      final rawReasons =
          analysis['reasons'];

      if (rawReasons is List) {
        for (final reason in rawReasons) {
          nativeReasons.add(
            '$reason',
          );
        }
      } else if (rawReasons is String &&
          rawReasons.isNotEmpty) {
        try {
          final decoded =
              jsonDecode(rawReasons);

          if (decoded is List) {
            for (final reason in decoded) {
              nativeReasons.add(
                '$reason',
              );
            }
          }
        } catch (_) {}
      }

      final label =
          nativeResult.isNotEmpty
              ? nativeResult
              : nativeLabel.isNotEmpty
                  ? nativeLabel
                  : aiScam
                      ? 'Suspicious message'
                      : 'No scam detected';

      debugPrint(
        'PAUSEAI NATIVE AI READY: $notificationId',
      );

      debugPrint(
        'PAUSEAI NATIVE AI SCAM: $aiScam',
      );

      debugPrint(
        'PAUSEAI NATIVE AI PROBABILITY: $probability',
      );

      debugPrint(
        'PAUSEAI NATIVE AI RISK SCORE: $nativeRiskScore',
      );

      debugPrint(
        'PAUSEAI NATIVE AI CATEGORY: $nativeCategory',
      );

      debugPrint(
        'PAUSEAI NATIVE URL RISK: $nativeUrlScore',
      );

      debugPrint(
        'PAUSEAI NATIVE HIGH RISK: $highRisk',
      );

      final extractedUrl =
          _extractUrl(latestMessage);

      int finalUrlScore =
          nativeUrlScore.round();

      if (extractedUrl != null &&
          finalUrlScore == 0 &&
          linkProtectionEnabled) {
        finalUrlScore =
            _calculateUrlRisk(
          extractedUrl,
        );
      }

      if (mounted) {
        setState(() {
          aiResult = label;

          aiConfidence = probability;

          riskScore =
              nativeRiskScore;

          riskLevel =
              nativeRiskLevel.isEmpty
                  ? highRisk
                      ? 'high'
                      : 'safe'
                  : nativeRiskLevel;

          category =
              nativeCategory;

          reasons =
              nativeReasons;

          recommendation =
              nativeRecommendation;

          aiAnalysing = false;

          highRiskDetected =
              highRisk;

          detectedUrl =
              extractedUrl ?? '';

          urlRiskScore =
              finalUrlScore;

          if (extractedUrl == null) {
            urlResult =
                'No URL detected';
          } else {
            urlResult =
                '${_urlRiskText(finalUrlScore)} ($finalUrlScore/100)';
          }

          paymentVerification =
              nativePayment;

          virusTotalReady =
              nativeVirusTotalReady;

          virusTotalMalicious =
              nativeVirusTotalMalicious;

          virusTotalSuspicious =
              nativeVirusTotalSuspicious;
        });
      }

      analyzedNotifications.add(
        notificationId,
      );

      try {
        await _channel.invokeMethod(
          'clearPendingNotifications',
          {
            'ids': [notificationId],
          },
        );

        debugPrint(
          'PAUSEAI NOTIFICATION CLEARED: $notificationId',
        );
      } catch (e) {
        debugPrint(
          'PAUSEAI CLEAR ERROR: $e',
        );
      }

      _activeNotificationId = null;
    } catch (e, stackTrace) {
      debugPrint(
        'PAUSEAI NATIVE ANALYSIS ERROR: $e',
      );

      debugPrint(
        'PAUSEAI NATIVE ANALYSIS STACK: $stackTrace',
      );

      if (mounted) {
        setState(() {
          aiAnalysing = false;
          aiResult = 'AI analysis error';
          aiConfidence = 0;
          riskScore = 0;
          riskLevel = 'error';
        });
      }

      _activeNotificationId = null;
    }
  }

  String? _extractUrl(String text) {
    final regex = RegExp(
      r'(https?://[^\s<>"]+|www\.[^\s<>"]+)',
      caseSensitive: false,
    );

    final match =
        regex.firstMatch(text);

    if (match == null) {
      return null;
    }

    var url =
        match.group(0)!.trim();

    while (url.endsWith('.') ||
        url.endsWith(',') ||
        url.endsWith('!') ||
        url.endsWith('?') ||
        url.endsWith(';') ||
        url.endsWith(':')) {
      url = url.substring(
        0,
        url.length - 1,
      );
    }

    while (url.endsWith(')') &&
        _countCharacter(url, ')') >
            _countCharacter(url, '(')) {
      url = url.substring(
        0,
        url.length - 1,
      );
    }

    return url;
  }

  int _countCharacter(
    String text,
    String character,
  ) {
    return character.allMatches(text).length;
  }

  int _calculateUrlRisk(String url) {
    final lower =
        url.toLowerCase();

    int score = 10;

    const shorteners = [
      'bit.ly',
      'tinyurl.com',
      't.co',
      'goo.gl',
      'is.gd',
      'cutt.ly',
      'shorturl.at',
      'rb.gy',
    ];

    const suspiciousWords = [
      'login',
      'verify',
      'verification',
      'claim',
      'reward',
      'prize',
      'winner',
      'bank',
      'payment',
      'refund',
      'account',
      'secure',
      'update',
      'gift',
      'bonus',
    ];

    const suspiciousTlds = [
      '.xyz',
      '.top',
      '.click',
      '.tk',
      '.ml',
      '.ga',
      '.cf',
      '.gq',
    ];

    for (final domain in shorteners) {
      if (lower.contains(domain)) {
        score = 90;
      }
    }

    for (final word in suspiciousWords) {
      if (lower.contains(word) &&
          score < 80) {
        score = 80;
      }
    }

    for (final tld in suspiciousTlds) {
      if (lower.contains(tld) &&
          score < 85) {
        score = 85;
      }
    }

    if (lower.contains('@') &&
        score < 90) {
      score = 90;
    }

    if (url.length > 120 &&
        score < 70) {
      score = 70;
    }

    return score;
  }

  String _urlRiskText(int score) {
    if (score >= 70) {
      return 'High-risk URL';
    }

    if (score >= 40) {
      return 'Potentially risky URL';
    }

    return 'No obvious URL risk';
  }

  Color _riskColor() {
    if (riskLevel == 'high' ||
        highRiskDetected ||
        riskScore >= 75) {
      return Colors.red;
    }

    if (riskLevel == 'suspicious' ||
        riskScore >= 45 ||
        urlRiskScore >= 40) {
      return Colors.orange;
    }

    return Colors.green;
  }

  IconData _riskIcon() {
    if (riskLevel == 'high' ||
        highRiskDetected ||
        riskScore >= 75) {
      return Icons.warning_rounded;
    }

    if (riskLevel == 'suspicious' ||
        riskScore >= 45) {
      return Icons.error_outline;
    }

    if (aiAnalysing) {
      return Icons.smart_toy;
    }

    return Icons.check_circle;
  }

  String _riskLabel() {
    if (aiAnalysing) {
      return 'ANALYSING';
    }

    if (riskLevel == 'high' ||
        highRiskDetected ||
        riskScore >= 75) {
      return 'HIGH RISK';
    }

    if (riskLevel == 'suspicious' ||
        riskScore >= 45) {
      return 'SUSPICIOUS';
    }

    if (riskLevel == 'error') {
      return 'ERROR';
    }

    return 'SAFE';
  }

  void _openProtectionFeatures() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            const ProtectionFeaturesScreen(),
      ),
    );
  }

  void _openHistory() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            const ScamHistoryScreen(),
      ),
    );
  }

  void _openSettings() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            SettingsScreen(
          darkMode:
              widget.darkMode,
          onThemeChanged:
              widget.onThemeChanged,
          protectionEnabled:
              protectionEnabled,
          linkProtectionEnabled:
              linkProtectionEnabled,
          paymentProtectionEnabled:
              paymentProtectionEnabled,
          onProtectionChanged:
              (value) async {
            await _saveSetting(
              'protectionEnabled',
              value,
            );

            if (mounted) {
              setState(() {
                protectionEnabled =
                    value;

                if (!value) {
                  _activeNotificationId =
                      null;
                }
              });
            }
          },
          onLinkChanged:
              (value) async {
            await _saveSetting(
              'linkProtectionEnabled',
              value,
            );

            if (mounted) {
              setState(() {
                linkProtectionEnabled =
                    value;
              });
            }
          },
          onPaymentChanged:
              (value) async {
            await _saveSetting(
              'paymentProtectionEnabled',
              value,
            );

            if (mounted) {
              setState(() {
                paymentProtectionEnabled =
                    value;
              });
            }
          },
        ),
      ),
    );
  }

  Widget _metric(
    String title,
    String value,
  ) {
    return Expanded(
      child: Container(
        padding:
            const EdgeInsets.all(12),
        decoration: BoxDecoration(
          borderRadius:
              BorderRadius.circular(12),
          color: Theme.of(context)
              .colorScheme
              .surfaceContainerHighest,
        ),
        child: Column(
          crossAxisAlignment:
              CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style:
                  const TextStyle(
                fontSize: 12,
              ),
            ),
            const SizedBox(
              height: 4,
            ),
            Text(
              value,
              maxLines: 2,
              overflow:
                  TextOverflow.ellipsis,
              style:
                  const TextStyle(
                fontWeight:
                    FontWeight.bold,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _sectionTitle(
    String title,
    IconData icon,
  ) {
    return Row(
      children: [
        Icon(icon),
        const SizedBox(
          width: 10,
        ),
        Text(
          title,
          style:
              const TextStyle(
            fontSize: 18,
            fontWeight:
                FontWeight.bold,
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final riskColor =
        _riskColor();

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'PauseAI',
          style:
              TextStyle(
            fontWeight:
                FontWeight.bold,
          ),
        ),
        actions: [
          PopupMenuButton<String>(
            onSelected:
                (value) {
              if (value ==
                  'features') {
                _openProtectionFeatures();
              } else if (value ==
                  'history') {
                _openHistory();
              } else if (value ==
                  'settings') {
                _openSettings();
              }
            },
            itemBuilder: (_) =>
                const [
              PopupMenuItem(
                value: 'features',
                child: Text(
                  'Protection Features',
                ),
              ),
              PopupMenuItem(
                value: 'history',
                child: Text(
                  'Scam History',
                ),
              ),
              PopupMenuItem(
                value: 'settings',
                child: Text(
                  'Settings',
                ),
              ),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh:
            _checkNotifications,
        child: ListView(
          padding:
              const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Row(
                  children: [
                    Icon(
                      protectionEnabled
                          ? Icons
                              .shield_rounded
                          : Icons
                              .shield_outlined,
                      size: 42,
                      color:
                          protectionEnabled
                              ? Colors.green
                              : Colors.grey,
                    ),
                    const SizedBox(
                      width: 14,
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment:
                            CrossAxisAlignment
                                .start,
                        children: [
                          const Text(
                            'Protection Status',
                            style:
                                TextStyle(
                              fontSize: 18,
                              fontWeight:
                                  FontWeight
                                      .bold,
                            ),
                          ),
                          const SizedBox(
                            height: 4,
                          ),
                          Text(
                            protectionEnabled
                                ? 'Protection is active'
                                : 'Protection is disabled',
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value:
                          protectionEnabled,
                      onChanged:
                          (value) async {
                        await _saveSetting(
                          'protectionEnabled',
                          value,
                        );

                        if (mounted) {
                          setState(() {
                            protectionEnabled =
                                value;

                            if (!value) {
                              _activeNotificationId =
                                  null;
                            }
                          });
                        }
                      },
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(
              height: 12,
            ),
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    _sectionTitle(
                      'Latest Notification',
                      Icons
                          .notifications_active,
                    ),
                    const SizedBox(
                      height: 12,
                    ),
                    Text(
                      latestApp.isEmpty
                          ? 'Waiting for notification'
                          : latestApp,
                      style:
                          const TextStyle(
                        fontWeight:
                            FontWeight
                                .bold,
                      ),
                    ),
                    if (latestTitle
                        .isNotEmpty) ...[
                      const SizedBox(
                        height: 4,
                      ),
                      Text(
                        latestTitle,
                      ),
                    ],
                    const SizedBox(
                      height: 8,
                    ),
                    Text(
                      latestMessage
                              .isEmpty
                          ? 'No notification detected'
                          : latestMessage,
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(
              height: 12,
            ),
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    _sectionTitle(
                      'AI Risk Analysis',
                      Icons
                          .smart_toy_rounded,
                    ),
                    const SizedBox(
                      height: 14,
                    ),
                    Container(
                      width:
                          double.infinity,
                      padding:
                          const EdgeInsets
                              .all(18),
                      decoration:
                          BoxDecoration(
                        borderRadius:
                            BorderRadius
                                .circular(
                          16,
                        ),
                        color: riskColor
                            .withOpacity(
                          0.10,
                        ),
                      ),
                      child: Column(
                        children: [
                          if (aiAnalysing)
                            const CircularProgressIndicator()
                          else
                            Icon(
                              _riskIcon(),
                              size: 48,
                              color:
                                  riskColor,
                            ),
                          const SizedBox(
                            height: 10,
                          ),
                          Text(
                            _riskLabel(),
                            style:
                                TextStyle(
                              fontSize: 20,
                              fontWeight:
                                  FontWeight
                                      .bold,
                              color:
                                  riskColor,
                            ),
                          ),
                          const SizedBox(
                            height: 6,
                          ),
                          Text(
                            aiResult,
                            textAlign:
                                TextAlign
                                    .center,
                            style:
                                const TextStyle(
                              fontWeight:
                                  FontWeight
                                      .bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (!aiAnalysing) ...[
                      const SizedBox(
                        height: 14,
                      ),
                      Row(
                        children: [
                          _metric(
                            'Risk Score',
                            '${riskScore.toStringAsFixed(0)}/100',
                          ),
                          const SizedBox(
                            width: 10,
                          ),
                          _metric(
                            'AI Confidence',
                            '${aiConfidence.toStringAsFixed(2)}%',
                          ),
                        ],
                      ),
                      const SizedBox(
                        height: 10,
                      ),
                      Row(
                        children: [
                          _metric(
                            'Category',
                            category,
                          ),
                          const SizedBox(
                            width: 10,
                          ),
                          _metric(
                            'Risk Level',
                            riskLevel
                                .toUpperCase(),
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
            if (reasons.isNotEmpty) ...[
              const SizedBox(
                height: 12,
              ),
              Card(
                child: Padding(
                  padding:
                      const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment:
                        CrossAxisAlignment
                            .start,
                    children: [
                      _sectionTitle(
                        'Why PauseAI Flagged It',
                        Icons
                            .search_rounded,
                      ),
                      const SizedBox(
                        height: 12,
                      ),
                      ...reasons.map(
                        (reason) =>
                            Padding(
                          padding:
                              const EdgeInsets
                                  .only(
                            bottom: 8,
                          ),
                          child: Row(
                            crossAxisAlignment:
                                CrossAxisAlignment
                                    .start,
                            children: [
                              Icon(
                                Icons.circle,
                                size: 8,
                                color:
                                    riskColor,
                              ),
                              const SizedBox(
                                width: 10,
                              ),
                              Expanded(
                                child:
                                    Text(
                                  reason,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            if (recommendation
                .isNotEmpty) ...[
              const SizedBox(
                height: 12,
              ),
              Card(
                child: Padding(
                  padding:
                      const EdgeInsets.all(18),
                  child: Column(
                    crossAxisAlignment:
                        CrossAxisAlignment
                            .start,
                    children: [
                      _sectionTitle(
                        'Recommendation',
                        Icons
                            .lightbulb_outline,
                      ),
                      const SizedBox(
                        height: 10,
                      ),
                      Text(
                        recommendation,
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(
              height: 12,
            ),
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    _sectionTitle(
                      'Link Protection',
                      Icons.link_rounded,
                    ),
                    const SizedBox(
                      height: 10,
                    ),
                    Text(
                      detectedUrl.isEmpty
                          ? 'No URL detected'
                          : detectedUrl,
                    ),
                    const SizedBox(
                      height: 10,
                    ),
                    Row(
                      children: [
                        Icon(
                          urlRiskScore >=
                                  70
                              ? Icons
                                  .dangerous
                              : urlRiskScore >=
                                      40
                                  ? Icons
                                      .warning
                                  : Icons
                                      .check_circle,
                          color:
                              urlRiskScore >=
                                      70
                                  ? Colors.red
                                  : urlRiskScore >=
                                          40
                                      ? Colors
                                          .orange
                                      : Colors
                                          .green,
                        ),
                        const SizedBox(
                          width: 8,
                        ),
                        Expanded(
                          child: Text(
                            urlResult,
                            style:
                                TextStyle(
                              color:
                                  riskColor,
                              fontWeight:
                                  FontWeight
                                      .bold,
                            ),
                          ),
                        ),
                      ],
                    ),
                    if (virusTotalReady) ...[
                      const SizedBox(
                        height: 12,
                      ),
                      Text(
                        'VirusTotal Results',
                        style:
                            const TextStyle(
                          fontWeight:
                              FontWeight
                                  .bold,
                        ),
                      ),
                      const SizedBox(
                        height: 6,
                      ),
                      Text(
                        'Malicious: $virusTotalMalicious',
                        style:
                            TextStyle(
                          color:
                              virusTotalMalicious >
                                      0
                                  ? Colors
                                      .red
                                  : Colors
                                      .green,
                          fontWeight:
                              FontWeight
                                  .bold,
                        ),
                      ),
                      Text(
                        'Suspicious: $virusTotalSuspicious',
                        style:
                            TextStyle(
                          color:
                              virusTotalSuspicious >
                                      0
                                  ? Colors
                                      .orange
                                  : Colors
                                      .green,
                          fontWeight:
                              FontWeight
                                  .bold,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            if (paymentVerification) ...[
              const SizedBox(
                height: 12,
              ),
              Card(
                child: Padding(
                  padding:
                      const EdgeInsets.all(18),
                  child: Row(
                    crossAxisAlignment:
                        CrossAxisAlignment
                            .start,
                    children: [
                      const Icon(
                        Icons.payment,
                        color:
                            Colors.orange,
                        size: 30,
                      ),
                      const SizedBox(
                        width: 12,
                      ),
                      const Expanded(
                        child: Text(
                          'Payment-related content detected. '
                          'Verify the sender and transaction independently '
                          'before sending money or sharing sensitive information.',
                          style:
                              TextStyle(
                            fontWeight:
                                FontWeight
                                    .w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(
              height: 12,
            ),
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    _sectionTitle(
                      'Protection Summary',
                      Icons
                          .security_rounded,
                    ),
                    const SizedBox(
                      height: 12,
                    ),
                    Row(
                      children: [
                        Icon(
                          highRiskDetected
                              ? Icons.warning
                              : Icons
                                  .check_circle,
                          color:
                              highRiskDetected
                                  ? Colors.red
                                  : Colors.green,
                        ),
                        const SizedBox(
                          width: 10,
                        ),
                        Expanded(
                          child: Text(
                            highRiskDetected
                                ? 'High-risk activity detected'
                                : 'No high-risk activity detected',
                            style:
                                TextStyle(
                              fontWeight:
                                  FontWeight
                                      .bold,
                              color:
                                  highRiskDetected
                                      ? Colors
                                          .red
                                      : Colors
                                          .green,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(
              height: 12,
            ),
            Card(
              child: Padding(
                padding:
                    const EdgeInsets.all(18),
                child: Row(
                  crossAxisAlignment:
                      CrossAxisAlignment
                          .start,
                  children: [
                    const Icon(
                      Icons.info_outline,
                    ),
                    const SizedBox(
                      width: 10,
                    ),
                    const Expanded(
                      child: Text(
                        'PauseAI provides automated risk detection. '
                        'Detection is not guaranteed. Always verify '
                        'important messages independently.',
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ProtectionFeaturesScreen
    extends StatelessWidget {
  const ProtectionFeaturesScreen({
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Protection Features',
        ),
      ),
      body: ListView(
        padding:
            const EdgeInsets.all(16),
        children: const [
          ListTile(
            leading:
                Icon(Icons.smart_toy),
            title: Text(
              'AI Message Analysis',
            ),
            subtitle: Text(
              'Analyzes notification messages for scam indicators.',
            ),
          ),
          ListTile(
            leading:
                Icon(Icons.link),
            title: Text(
              'Link Protection',
            ),
            subtitle: Text(
              'Checks URLs using local risk analysis and VirusTotal.',
            ),
          ),
          ListTile(
            leading:
                Icon(Icons.payment),
            title: Text(
              'Payment Protection',
            ),
            subtitle: Text(
              'Detects risky payment, OTP and money-transfer requests.',
            ),
          ),
          ListTile(
            leading: Icon(
              Icons.notifications_active,
            ),
            title: Text(
              'Background Protection',
            ),
            subtitle: Text(
              'Notification monitoring can continue without opening PauseAI.',
            ),
          ),
          ListTile(
            leading:
                Icon(Icons.history),
            title: Text(
              'Scam History',
            ),
            subtitle: Text(
              'Stores high-risk detections for later review.',
            ),
          ),
        ],
      ),
    );
  }
}

class ScamHistoryScreen
    extends StatefulWidget {
  const ScamHistoryScreen({
    super.key,
  });

  @override
  State<ScamHistoryScreen> createState() =>
      _ScamHistoryScreenState();
}

class _ScamHistoryScreenState
    extends State<ScamHistoryScreen> {
  static const MethodChannel _channel =
      MethodChannel(
    'pauseai/notifications',
  );

  List<dynamic> history = [];
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    try {
      final result =
          await _channel.invokeMethod(
        'getScamHistory',
      );

      if (result is String) {
        final decoded =
            jsonDecode(result);

        if (decoded is List) {
          history = decoded;
        }
      } else if (result is List) {
        history = result;
      }
    } catch (e) {
      debugPrint(
        'PAUSEAI HISTORY ERROR: $e',
      );

      history = [];
    }

    if (mounted) {
      setState(() {
        loading = false;
      });
    }
  }

  Future<void> _clearHistory() async {
    await _channel.invokeMethod(
      'clearScamHistory',
    );

    if (mounted) {
      setState(() {
        history = [];
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title:
            const Text('Scam History'),
        actions: [
          if (history.isNotEmpty)
            IconButton(
              icon:
                  const Icon(Icons.delete),
              onPressed:
                  _clearHistory,
            ),
        ],
      ),
      body: loading
          ? const Center(
              child:
                  CircularProgressIndicator(),
            )
          : history.isEmpty
              ? const Center(
                  child: Text(
                    'No high-risk detections yet',
                  ),
                )
              : ListView.builder(
                  padding:
                      const EdgeInsets.all(
                    12,
                  ),
                  itemCount:
                      history.length,
                  itemBuilder:
                      (context, index) {
                    final raw =
                        history[index];

                    if (raw is! Map) {
                      return const SizedBox
                          .shrink();
                    }

                    final item =
                        Map<String, dynamic>
                            .from(raw);

                    final probability =
                        double.tryParse(
                              '${item['probability'] ?? 0}',
                            ) ??
                            0;

                    final historyRisk =
                        double.tryParse(
                              '${item['riskScore'] ?? item['aiRiskScore'] ?? 0}',
                            ) ??
                            0;

                    final urlScore =
                        double.tryParse(
                              '${item['urlRiskScore'] ?? 0}',
                            ) ??
                            0;

                    final historyCategory =
                        '${item['category'] ?? 'General Message'}';

                    final historyRiskLevel =
                        '${item['riskLevel'] ?? 'high'}';

                    final rawReasons =
                        item['reasons'];

                    final List<String>
                        historyReasons = [];

                    if (rawReasons
                        is List) {
                      for (final reason
                          in rawReasons) {
                        historyReasons
                            .add(
                          '$reason',
                        );
                      }
                    }

                    return Card(
                      margin:
                          const EdgeInsets
                              .only(
                        bottom: 12,
                      ),
                      child: Padding(
                        padding:
                            const EdgeInsets
                                .all(16),
                        child: Column(
                          crossAxisAlignment:
                              CrossAxisAlignment
                                  .start,
                          children: [
                            Row(
  children: [
    const Icon(
      Icons.warning,
      color: Colors.red,
    ),
    const SizedBox(
      width: 8,
    ),
    Expanded(
      child: Column(
        crossAxisAlignment:
            CrossAxisAlignment.start,
        children: [
          Text(
            '${item['app'] ?? item['packageName'] ?? 'Unknown app'}',
            style: const TextStyle(
              fontWeight: FontWeight.bold,
            ),
          ),
          if ('${item['title'] ?? ''}'.isNotEmpty) ...[
            const SizedBox(
              height: 4,
            ),
            Text(
              'From: ${item['title']}',
              style: const TextStyle(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ],
      ),
    ),
  ],
),
                            const SizedBox(
                              height: 8,
                            ),
                            Text(
                              '${item['message'] ?? ''}',
                            ),
                            const SizedBox(
                              height: 10,
                            ),
                            Text(
                              'Category: $historyCategory',
                            ),
                            Text(
                              'Risk level: ${historyRiskLevel.toUpperCase()}',
                            ),
                            Text(
                              'Risk score: ${historyRisk.toStringAsFixed(0)}/100',
                            ),
                            Text(
                              'AI confidence: ${probability.toStringAsFixed(2)}%',
                            ),
                            if (urlScore >
                                0)
                              Text(
                                'URL risk: ${urlScore.toStringAsFixed(0)}/100',
                              ),
                            if (historyReasons
                                .isNotEmpty) ...[
                              const SizedBox(
                                height: 8,
                              ),
                              const Text(
                                'Reasons:',
                                style:
                                    TextStyle(
                                  fontWeight:
                                      FontWeight
                                          .bold,
                                ),
                              ),
                              ...historyReasons
                                  .map(
                                (reason) =>
                                    Text(
                                  '• $reason',
                                ),
                              ),
                            ],
                            if ('${item['recommendation'] ?? ''}'
                                .isNotEmpty) ...[
                              const SizedBox(
                                height: 8,
                              ),
                              Text(
                                '${item['recommendation']}',
                              ),
                            ],
                          ],
                        ),
                      ),
                    );
                  },
                ),
    );
  }
}

class SettingsScreen
    extends StatefulWidget {
  final bool darkMode;
  final Future<void> Function(bool)
      onThemeChanged;

  final bool protectionEnabled;
  final bool linkProtectionEnabled;
  final bool paymentProtectionEnabled;

  final Future<void> Function(bool)
      onProtectionChanged;
  final Future<void> Function(bool)
      onLinkChanged;
  final Future<void> Function(bool)
      onPaymentChanged;

  const SettingsScreen({
    super.key,
    required this.darkMode,
    required this.onThemeChanged,
    required this.protectionEnabled,
    required this.linkProtectionEnabled,
    required this.paymentProtectionEnabled,
    required this.onProtectionChanged,
    required this.onLinkChanged,
    required this.onPaymentChanged,
  });

  @override
  State<SettingsScreen> createState() =>
      _SettingsScreenState();
}

class _SettingsScreenState
    extends State<SettingsScreen> {
  late bool darkMode;
  late bool protectionEnabled;
  late bool linkProtectionEnabled;
  late bool paymentProtectionEnabled;

  @override
  void initState() {
    super.initState();

    darkMode =
        widget.darkMode;

    protectionEnabled =
        widget.protectionEnabled;

    linkProtectionEnabled =
        widget.linkProtectionEnabled;

    paymentProtectionEnabled =
        widget.paymentProtectionEnabled;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title:
            const Text('Settings'),
      ),
      body: ListView(
        children: [
          SwitchListTile(
            title: const Text(
              'Protection',
            ),
            subtitle: const Text(
              'Enable notification protection',
            ),
            value:
                protectionEnabled,
            onChanged:
                (value) async {
              setState(() {
                protectionEnabled =
                    value;
              });

              await widget
                  .onProtectionChanged(
                value,
              );
            },
          ),
          SwitchListTile(
            title: const Text(
              'Link Protection',
            ),
            subtitle: const Text(
              'Analyze URLs inside notifications',
            ),
            value:
                linkProtectionEnabled,
            onChanged:
                (value) async {
              setState(() {
                linkProtectionEnabled =
                    value;
              });

              await widget
                  .onLinkChanged(
                value,
              );
            },
          ),
          SwitchListTile(
            title: const Text(
              'Payment Protection',
            ),
            subtitle: const Text(
              'Detect risky payment and OTP requests',
            ),
            value:
                paymentProtectionEnabled,
            onChanged:
                (value) async {
              setState(() {
                paymentProtectionEnabled =
                    value;
              });

              await widget
                  .onPaymentChanged(
                value,
              );
            },
          ),
          SwitchListTile(
            title: const Text(
              'Dark Mode',
            ),
            value: darkMode,
            onChanged:
                (value) async {
              setState(() {
                darkMode = value;
              });

              await widget
                  .onThemeChanged(
                value,
              );
            },
          ),
        ],
      ),
    );
  }
}