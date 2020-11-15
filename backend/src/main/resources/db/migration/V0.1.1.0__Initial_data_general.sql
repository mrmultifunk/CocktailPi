INSERT INTO ingredients (id, alcohol_content, name, is_syrup)
VALUES (1, 0, 'Cola', false)
     , (2, 38, 'Triple sec', false)
     , (3, 40, 'Tequila', false)
     , (4, 40, 'Whiskey', false)
     , (5, 40, 'Vodka', false)
     , (6, 0, 'Cranberry Juice', false)
     , (7, 38, 'Gin', false)
     , (8, 25, 'Campari', false)
     , (9, 20, 'Sweet Vermouth', false)
     , (14, 12, 'Champagne', false)
     , (10, 0, 'Ginger Beer', false)
     , (12, 0, 'Lemon Juice', false)
     , (11, 0, 'Lime Juice', false)
     , (15, 0, 'Orange Juice', false)
     , (13, 0, 'Simple Syrup', false)
     , (16, 16, 'Dry Vermouth', false)
     , (17, 38, 'Dark Rum', false)
     , (18, 40, 'Cognac', false)
     , (19, 38, 'Silver Tequila', false)
     , (20, 0, 'Pineapple Juice', false)
     , (21, 5, 'Mexican Beer', false)
     , (22, 21, 'Blue Curaçao', false)
     , (23, 40, 'Rum', false)
     , (24, 75, 'Gold Rum', false)
     , (25, 0, 'Red Bull', false)
     , (26, 17, 'Chambord Liqueur', false)
     , (27, 21, 'Malibu', false)
     , (28, 0, 'Sprite', false)
     , (29, 18, 'Peach Schnapps', false)
     , (30, 40, 'Cointreau', false)
     , (31, 40, 'Citrus Codka', false)
     , (32, 40, 'Grand Marnier', false)
     , (33, 0, 'Cranberry Nectar', false)
     , (34, 24, 'Apricot Brandy', false)
     , (35, 0, 'Maracuja Nectar', false)
     , (36, 0, 'Grenadine Syrup', false)
     , (37, 20, 'Kahlúa', false)
     , (38, 17, 'Baileys', false)
     , (39, 40, 'Cream', false)
     , (40, 20, 'Peach Liquor', false);
SELECT setval('ingredients_id_seq', 40, true);

INSERT INTO categories (id, name)
VALUES (1, 'Classics')
    , (2, 'Vodka drinks')
    , (3, 'Fancy drinks');
SELECT setval('categories_id_seq', 3, true);

INSERT INTO users (id, email, firstname, is_account_non_locked, lastname, password, role, username)
VALUES (1, 'admin@localhost.local', 'Admin', true, 'Example',
        '$2a$10$foQL7xSRCK53J/G.MIauWOnllOS9.vyIT5RtUQT25t5ref07MtCfe', 'ROLE_ADMIN', 'admin');
SELECT setval('users_id_seq', 1, true);

INSERT INTO recipes (id, description, in_public, name, owner_id, last_update)
VALUES (1, 'Tasty Mix of Whatever you Have on Hand with a Caffeine Boost!', true, 'Trash Can', 1, CURRENT_TIMESTAMP);
INSERT INTO recipe_ingredients (ingredient_id, recipe_id, production_step, amount)
VALUES (2, 1, 1, 15)
     , (5, 1, 1, 15)
     , (7, 1, 1, 15)
     , (22, 1, 1, 15)
     , (25, 1, 1, 220)
     , (29, 1, 1, 15)
     , (23, 1, 1, 15);