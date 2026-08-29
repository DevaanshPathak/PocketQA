import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'config/app_config.dart';
import 'state/cart_provider.dart';
import 'ui/screens/catalog_screen.dart';
import 'ui/screens/cart_screen.dart';
import 'ui/screens/checkout_screen.dart';
import 'ui/screens/order_success_screen.dart';

class QuickCommerceApp extends StatelessWidget {
  final AppConfig config;

  const QuickCommerceApp({super.key, required this.config});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => CartProvider(injectBugs: config.injectBugs),
        ),
      ],
      child: MaterialApp(
        title: config.appTitle,
        theme: ThemeData(
          primarySwatch: Colors.green,
          useMaterial3: true,
        ),
        builder: (context, child) => AppConfig(
          injectBugs: config.injectBugs,
          appTitle: config.appTitle,
          child: child!,
        ),
        initialRoute: '/',
        routes: {
          '/': (context) => CatalogScreen(),
          '/cart': (context) => const CartScreen(),
          '/checkout': (context) => const CheckoutScreen(),
          '/order-success': (context) => const OrderSuccessScreen(),
        },
      ),
    );
  }
}
