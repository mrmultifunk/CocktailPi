INSERT INTO ingredients (id, dtype, alcohol_content, name, unit, pump_time_multiplier)
VALUES (1, 'AutomatedIngredient', 0, 'Cola', 'MILLILITER', 1)
     , (2, 'AutomatedIngredient', 38, 'Triple sec', 'MILLILITER', 1)
     , (3, 'AutomatedIngredient', 40, 'Tequila', 'MILLILITER', 1)
     , (4, 'AutomatedIngredient', 40, 'Whiskey', 'MILLILITER', 1)
     , (5, 'AutomatedIngredient', 40, 'Vodka', 'MILLILITER', 1)
     , (6, 'AutomatedIngredient', 0, 'Cranberry Juice', 'MILLILITER', 1)
     , (7, 'AutomatedIngredient', 38, 'Gin', 'MILLILITER', 1)
     , (8, 'AutomatedIngredient', 25, 'Campari', 'MILLILITER', 1)
     , (9, 'AutomatedIngredient', 20, 'Sweet Vermouth', 'MILLILITER', 1)
     , (14, 'AutomatedIngredient', 12, 'Champagne', 'MILLILITER', 1)
     , (10, 'AutomatedIngredient', 0, 'Ginger Beer', 'MILLILITER', 1)
     , (12, 'AutomatedIngredient', 0, 'Lemon Juice', 'MILLILITER', 1)
     , (11, 'AutomatedIngredient', 0, 'Lime Juice', 'MILLILITER', 1)
     , (15, 'AutomatedIngredient', 0, 'Orange Juice', 'MILLILITER', 1)
     , (13, 'AutomatedIngredient', 0, 'Simple Syrup', 'MILLILITER', 1)
     , (16, 'AutomatedIngredient', 16, 'Dry Vermouth', 'MILLILITER', 1)
     , (17, 'AutomatedIngredient', 38, 'Dark Rum', 'MILLILITER', 1)
     , (18, 'AutomatedIngredient', 40, 'Cognac', 'MILLILITER', 1)
     , (19, 'AutomatedIngredient', 38, 'Silver Tequila', 'MILLILITER', 1)
     , (20, 'AutomatedIngredient', 0, 'Pineapple Juice', 'MILLILITER', 1)
     , (21, 'AutomatedIngredient', 5, 'Mexican Beer', 'MILLILITER', 1)
     , (22, 'AutomatedIngredient', 21, 'Blue Curaçao', 'MILLILITER', 1)
     , (23, 'AutomatedIngredient', 40, 'Rum', 'MILLILITER', 1)
     , (24, 'AutomatedIngredient', 75, 'Gold Rum', 'MILLILITER', 1)
     , (25, 'AutomatedIngredient', 0, 'Red Bull', 'MILLILITER', 1)
     , (26, 'AutomatedIngredient', 17, 'Chambord Liqueur', 'MILLILITER', 1)
     , (27, 'AutomatedIngredient', 21, 'Malibu', 'MILLILITER', 1)
     , (28, 'AutomatedIngredient', 0, 'Sprite', 'MILLILITER', 1)
     , (29, 'AutomatedIngredient', 18, 'Peach Schnapps', 'MILLILITER', 1)
     , (30, 'AutomatedIngredient', 40, 'Cointreau', 'MILLILITER', 1)
     , (31, 'AutomatedIngredient', 40, 'Citrus Vodka', 'MILLILITER', 1)
     , (32, 'AutomatedIngredient', 40, 'Grand Marnier', 'MILLILITER', 1)
     , (33, 'AutomatedIngredient', 0, 'Cranberry Nectar', 'MILLILITER', 1)
     , (34, 'AutomatedIngredient', 24, 'Apricot Brandy', 'MILLILITER', 1)
     , (35, 'AutomatedIngredient', 0, 'Maracuja Nectar', 'MILLILITER', 1)
     , (36, 'AutomatedIngredient', 0, 'Grenadine Syrup', 'MILLILITER', 1)
     , (37, 'AutomatedIngredient', 20, 'Kahlúa', 'MILLILITER', 1)
     , (38, 'AutomatedIngredient', 17, 'Baileys', 'MILLILITER', 1)
     , (39, 'AutomatedIngredient', 40, 'Cream', 'MILLILITER', 1)
     , (40, 'AutomatedIngredient', 20, 'Peach Liquor', 'MILLILITER', 1)
     , (41, 'AutomatedIngredient', 0, 'Lime Syrup', 'MILLILITER', 1)
     , (42, 'AutomatedIngredient', 30, 'Amaretto', 'MILLILITER', 1);
SELECT setval('ingredients_id_seq', 42, true);

INSERT INTO categories (id, name)
VALUES (1, 'Classics')
    , (2, 'Vodka drinks')
    , (3, 'Fancy drinks');
SELECT setval('categories_id_seq', 3, true);

INSERT INTO users (id, email, firstname, is_account_non_locked, lastname, password, role, username)
VALUES (1, 'admin@localhost.local', 'Admin', true, 'Example',
        '$2a$10$foQL7xSRCK53J/G.MIauWOnllOS9.vyIT5RtUQT25t5ref07MtCfe', 'ROLE_ADMIN', 'admin');
SELECT setval('users_id_seq', 1, true);