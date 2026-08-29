import 'package:flutter/material.dart';

class AppConfig extends InheritedWidget {
  final bool injectBugs;
  final String appTitle;

  const AppConfig({
    super.key,
    required this.injectBugs,
    required this.appTitle,
    required super.child,
  });

  static AppConfig? of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<AppConfig>();
  }

  @override
  bool updateShouldNotify(AppConfig oldWidget) {
    return oldWidget.injectBugs != injectBugs || oldWidget.appTitle != appTitle;
  }
}
