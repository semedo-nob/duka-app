package com.duka.security;

import com.duka.domain.UserRole;

import java.util.EnumSet;
import java.util.Set;

public final class RolePermissions {
    private RolePermissions() {}

    public static Set<Permission> defaults(UserRole role) {
        return switch (role) {
            case OWNER -> EnumSet.allOf(Permission.class);
            case MANAGER -> EnumSet.of(
                    Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT,
                    Permission.INVENTORY_VIEW, Permission.INVENTORY_ADJUST, Permission.INVENTORY_RECEIVE,
                    Permission.SALES_VIEW, Permission.SALE_CREATE, Permission.SALE_RETURN,
                    Permission.CUSTOMER_VIEW, Permission.CUSTOMER_CREATE, Permission.CUSTOMER_EDIT,
                    Permission.SUPPLIER_VIEW, Permission.SUPPLIER_CREATE, Permission.SUPPLIER_EDIT,
                    Permission.PURCHASE_VIEW, Permission.PURCHASE_CREATE, Permission.PURCHASE_APPROVE,
                    Permission.EXPENSE_VIEW, Permission.EXPENSE_CREATE,
                    Permission.REPORT_VIEW, Permission.REPORT_FINANCIAL,
                    Permission.USER_VIEW, Permission.ROLE_VIEW);
            case STOREKEEPER, INVENTORY -> EnumSet.of(
                    Permission.PRODUCT_VIEW, Permission.PRODUCT_CREATE, Permission.PRODUCT_EDIT,
                    Permission.INVENTORY_VIEW, Permission.INVENTORY_ADJUST, Permission.INVENTORY_RECEIVE,
                    Permission.SUPPLIER_VIEW, Permission.SUPPLIER_CREATE, Permission.SUPPLIER_EDIT,
                    Permission.PURCHASE_VIEW, Permission.PURCHASE_CREATE, Permission.PURCHASE_APPROVE,
                    Permission.CUSTOMER_VIEW);
            case CASHIER -> EnumSet.of(
                    Permission.PRODUCT_VIEW,
                    Permission.SALES_VIEW, Permission.SALE_CREATE,
                    Permission.CUSTOMER_VIEW, Permission.CUSTOMER_CREATE);
        };
    }
}
