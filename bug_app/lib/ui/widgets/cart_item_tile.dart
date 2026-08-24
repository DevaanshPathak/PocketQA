import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../state/cart_provider.dart';

class CartItemTile extends StatelessWidget {
  final String productId;
  final CartItem cartItem;

  const CartItemTile({
    super.key,
    required this.productId,
    required this.cartItem,
  });

  @override
  Widget build(BuildContext context) {
    final cart = Provider.of<CartProvider>(context, listen: false);
    return ListTile(
      leading: Image.network(
        cartItem.product.imageUrl,
        width: 50,
        errorBuilder: (context, error, stackTrace) => const Icon(Icons.shopping_basket),
      ),
      title: Text(cartItem.product.title, overflow: TextOverflow.ellipsis),
      subtitle: Text('Total: \$${(cartItem.product.price * cartItem.quantity).toStringAsFixed(2)}'),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: const Icon(Icons.remove),
            onPressed: () => cart.decrementItem(productId),
          ),
          Text('${cartItem.quantity}'),
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => cart.addItem(cartItem.product),
          ),
        ],
      ),
    );
  }
}
