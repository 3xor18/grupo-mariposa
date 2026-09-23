import 'package:flutter/material.dart';
import 'package:order_tracker/app/shell_keys.dart';
import 'package:order_tracker/core/l10n/app_strings.dart';
import 'package:order_tracker/core/theme/app_tokens.dart';
import 'package:order_tracker/features/auth/domain/auth_user.dart';
import 'package:order_tracker/features/auth/presentation/user_menu.dart';
import 'package:order_tracker/features/orders/presentation/pages/order_search_page.dart';
import 'package:order_tracker/features/orders/presentation/pages/orders_list_page.dart';

final class _Destination {
  const _Destination({required this.icon, required this.selectedIcon, required this.label});

  final IconData icon;
  final IconData selectedIcon;
  final String label;
}

class HomeShell extends StatefulWidget {
  const HomeShell({required this.user, super.key});

  final AuthUser user;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  static const _searchIndex = 0;
  static const _ordersIndex = 1;
  static const _destinations = [
    _Destination(
      icon: Icons.search_outlined,
      selectedIcon: Icons.search,
      label: AppStrings.search,
    ),
    _Destination(
      icon: Icons.list_alt_outlined,
      selectedIcon: Icons.list_alt,
      label: AppStrings.navOrders,
    ),
  ];

  int _selectedIndex = _searchIndex;
  final _visited = <int>{_searchIndex};

  void _select(int index) {
    setState(() {
      _selectedIndex = index;
      _visited.add(index);
    });
  }

  Widget _page(int index, Widget page) => _visited.contains(index) ? page : const SizedBox.shrink();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= AppBreakpoints.medium;
        final body = IndexedStack(
          index: _selectedIndex,
          children: [
            _page(_searchIndex, const OrderSearchPage()),
            _page(_ordersIndex, const OrdersListPage()),
          ],
        );
        return Scaffold(
          appBar: AppBar(
            title: const Text(AppStrings.appTitle),
            actions: [UserMenu(user: widget.user)],
          ),
          body: SafeArea(child: wide ? _withRail(body) : body),
          bottomNavigationBar: wide ? null : _navigationBar(),
        );
      },
    );
  }

  Widget _withRail(Widget body) {
    return Row(
      children: [
        NavigationRail(
          key: ShellKeys.navigationRail,
          selectedIndex: _selectedIndex,
          onDestinationSelected: _select,
          labelType: NavigationRailLabelType.all,
          destinations: [
            for (final destination in _destinations)
              NavigationRailDestination(
                icon: Icon(destination.icon),
                selectedIcon: Icon(destination.selectedIcon),
                label: Text(destination.label),
              ),
          ],
        ),
        const VerticalDivider(width: AppSizes.dividerThickness),
        Expanded(child: body),
      ],
    );
  }

  Widget _navigationBar() {
    return NavigationBar(
      key: ShellKeys.navigationBar,
      selectedIndex: _selectedIndex,
      onDestinationSelected: _select,
      destinations: [
        for (final destination in _destinations)
          NavigationDestination(
            icon: Icon(destination.icon),
            selectedIcon: Icon(destination.selectedIcon),
            label: destination.label,
          ),
      ],
    );
  }
}
