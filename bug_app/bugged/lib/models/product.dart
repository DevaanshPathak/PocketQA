class Product {
  final String id;
  final String title;
  final double price;
  final int stock;
  final String imageUrl;

  Product({
    required this.id,
    required this.title,
    required this.price,
    required this.stock,
    required this.imageUrl,
  });

  String get name => title;
  String get brand => 'QuickCart';
  String get quantityLabel => '1 unit';
  double get originalPrice => price;
  int get discountPercentage => 0;

  factory Product.fromMap(Map<String, dynamic> data, String documentId) {
    return Product(
      id: documentId,
      title: data['title'] ?? '',
      price: (data['price'] ?? 0.0).toDouble(),
      stock: (data['stock'] ?? 0).toInt(),
      imageUrl: data['imageUrl'] ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'title': title,
      'price': price,
      'stock': stock,
      'imageUrl': imageUrl,
    };
  }
}
