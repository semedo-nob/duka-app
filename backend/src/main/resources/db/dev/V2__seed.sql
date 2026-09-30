INSERT INTO categories (id, name) VALUES
    (1, 'Dairy'),
    (2, 'Bakery'),
    (3, 'Grocery'),
    (4, 'Household'),
    (5, 'Beverages');

INSERT INTO products (id, name, sku, barcode, category_id, unit, cost, price, tax_rate, reorder_level, emoji, color) VALUES
    (1,  'Milk 500ml',          'DAI-001', '6161100000001', 1, 'each', 52,  65,  0.16, 20, '🥛', '#EAF2EE'),
    (2,  'White Bread',         'BAK-014', '6161100000002', 2, 'each', 55,  70,  0.16, 20, '🍞', '#FBEDD6'),
    (3,  'Sugar 1kg',           'GRO-022', '6161100000003', 3, 'each', 155, 180, 0.16, 15, '🧂', '#EDEBE3'),
    (4,  'Cooking Oil 2L',      'GRO-031', '6161100000004', 3, 'each', 470, 520, 0.16, 10, '🫙', '#FBF0DE'),
    (5,  'Rice 2kg',            'GRO-018', '6161100000005', 3, 'each', 295, 340, 0.16, 12, '🍚', '#EAF2EE'),
    (6,  'Bar Soap',            'HOU-005', '6161100000006', 4, 'each', 44,  60,  0.16, 20, '🧼', '#E4EEEA'),
    (7,  'Maize Flour 2kg',     'GRO-009', '6161100000007', 3, 'each', 185, 215, 0.16, 15, '🌽', '#FBEDD6'),
    (8,  'Soda 500ml',          'BEV-002', '6161100000008', 5, 'each', 55,  70,  0.16, 24, '🥤', '#FBE7E3'),
    (9,  'Eggs (Tray)',         'DAI-007', '6161100000009', 1, 'tray', 370, 420, 0.16, 10, '🥚', '#FBF0DE'),
    (10, 'Tea Leaves 100g',     'BEV-011', '6161100000010', 5, 'each', 76,  95,  0.16, 15, '🍵', '#E4EEEA'),
    (11, 'Washing Powder 1kg',  'HOU-012', '6161100000011', 4, 'each', 220, 260, 0.16, 12, '🧺', '#EDEBE3'),
    (12, 'Bananas (bunch)',     'GRO-044', '6161100000012', 3, 'bunch', 90, 120, 0.00, 10, '🍌', '#FBEDD6');

INSERT INTO app_settings (key, value) VALUES
    ('prevent_negative_stock', 'true'),
    ('cap.purchasing', 'false'),
    ('cap.credit', 'false'),
    ('cap.advReports', 'false'),
    ('cap.multiBranch', 'false'),
    ('cap.etims', 'false'),
    ('business', '{"name":"Mama Njeri Store","type":"Retail shop","address":"","phone":"0712 345 678"}'),
    ('payments', '{"mpesaTill":"","cardTerminal":"Not connected","defaultMethod":"Cash"}'),
    ('receipts', '{"footer":"Asante for shopping with us!","showLogo":"Yes","copies":"1"}'),
    ('tax', '{"pin":"","taxCategory":"Standard VAT (16%)"}');

INSERT INTO inventory_balances (product_id, quantity) VALUES
    (1, 42), (2, 18), (3, 65), (4, 24), (5, 31), (6, 80),
    (7, 8), (8, 56), (9, 14), (10, 47), (11, 19), (12, 26);

INSERT INTO inventory_movements (product_id, movement_type, quantity, unit_cost, reference_type, reference_id, note) VALUES
    (1,  'OPENING_BALANCE', 42, 52,  'SEED', 'opening', 'Opening balance'),
    (2,  'OPENING_BALANCE', 18, 55,  'SEED', 'opening', 'Opening balance'),
    (3,  'OPENING_BALANCE', 65, 155, 'SEED', 'opening', 'Opening balance'),
    (4,  'OPENING_BALANCE', 24, 470, 'SEED', 'opening', 'Opening balance'),
    (5,  'OPENING_BALANCE', 31, 295, 'SEED', 'opening', 'Opening balance'),
    (6,  'OPENING_BALANCE', 80, 44,  'SEED', 'opening', 'Opening balance'),
    (7,  'OPENING_BALANCE', 8,  185, 'SEED', 'opening', 'Opening balance'),
    (8,  'OPENING_BALANCE', 56, 55,  'SEED', 'opening', 'Opening balance'),
    (9,  'OPENING_BALANCE', 14, 370, 'SEED', 'opening', 'Opening balance'),
    (10, 'OPENING_BALANCE', 47, 76,  'SEED', 'opening', 'Opening balance'),
    (11, 'OPENING_BALANCE', 19, 220, 'SEED', 'opening', 'Opening balance'),
    (12, 'OPENING_BALANCE', 26, 90,  'SEED', 'opening', 'Opening balance');

INSERT INTO suppliers (id, name, contact, products) VALUES
    (1, 'Kamau Wholesalers', '0722 100 200', 'Grocery, Grains'),
    (2, 'Nakumatt Distributors', '0733 400 500', 'Household, Beverages');

INSERT INTO customers (id, name, phone, balance) VALUES
    (1, 'Wanjiru Kamau', '0700 111 222', 0),
    (2, 'Otieno Mercantile', '0711 222 333', 0),
    (3, 'Kiptoo General Store', '0722 333 444', 0);

INSERT INTO branches (id, name, sales, staff, low_stock) VALUES
    (1, 'Nairobi — Moi Avenue', 0, 1, 0),
    (2, 'Mombasa — Nyali', 0, 1, 0),
    (3, 'Kisumu — CBD', 0, 1, 0);

INSERT INTO warehouses (id, name, branch, stock_value) VALUES
    (1, 'Nairobi Warehouse', 'Nairobi — Moi Avenue', 0),
    (2, 'Mombasa Warehouse', 'Mombasa — Nyali', 0);

INSERT INTO team_members (id, name, phone, role, status) VALUES
    ('tm-amina', 'Amina Njeri', '0712 345 678', 'Owner', 'Active'),
    ('tm-brian', 'Brian Otieno', '0722 456 789', 'Manager', 'Active'),
    ('tm-faith', 'Faith Achieng', '0733 567 890', 'Cashier', 'Active'),
    ('tm-peter', 'Peter Mwangi', '0744 678 901', 'Cashier', 'Off shift');

INSERT INTO audit_events (id, actor, action, detail) VALUES
    ('audit-open', 'System', 'posted opening balances', 'Seed catalogue. Each product quantity is explained by an OPENING_BALANCE movement.');

SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
SELECT setval('suppliers_id_seq', (SELECT MAX(id) FROM suppliers));
SELECT setval('customers_id_seq', (SELECT MAX(id) FROM customers));
SELECT setval('branches_id_seq', (SELECT MAX(id) FROM branches));
SELECT setval('warehouses_id_seq', (SELECT MAX(id) FROM warehouses));
SELECT setval('sales_id_seq', 10482);
