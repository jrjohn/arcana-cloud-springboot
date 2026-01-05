// MongoDB initialization script for arcana_cloud database
// Run with: mongosh < init-mongo.js

// Switch to arcana_cloud database
db = db.getSiblingDB('arcana_cloud');

// Create users collection with schema validation
db.createCollection('users', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['username', 'email', 'password'],
            properties: {
                username: {
                    bsonType: 'string',
                    minLength: 1,
                    maxLength: 50
                },
                email: {
                    bsonType: 'string',
                    pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$'
                },
                password: {
                    bsonType: 'string',
                    minLength: 8
                },
                role: {
                    enum: ['USER', 'ADMIN', 'MODERATOR']
                }
            }
        }
    }
});

// Create indexes for users collection
db.users.createIndex({ username: 1 }, { unique: true });
db.users.createIndex({ email: 1 }, { unique: true });
db.users.createIndex({ role: 1 });
db.users.createIndex({ is_active: 1 });
db.users.createIndex({ legacy_id: 1 }, { unique: true, sparse: true });

// Create oauth_tokens collection
db.createCollection('oauth_tokens');

// Create indexes for oauth_tokens collection
db.oauth_tokens.createIndex({ access_token: 1 });
db.oauth_tokens.createIndex({ refresh_token: 1 });
db.oauth_tokens.createIndex({ user_legacy_id: 1 });
db.oauth_tokens.createIndex({ expires_at: 1 });
db.oauth_tokens.createIndex({ is_revoked: 1 });
db.oauth_tokens.createIndex({ legacy_id: 1 }, { unique: true, sparse: true });

// Insert default admin user (password: Admin@123)
db.users.insertOne({
    legacy_id: NumberLong(1),
    username: 'admin',
    email: 'admin@arcana-cloud.com',
    password: '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3NPOSNCiPb8K2pQX6I/6',
    first_name: 'System',
    last_name: 'Administrator',
    role: 'ADMIN',
    is_active: true,
    is_verified: true,
    created_at: new Date(),
    updated_at: new Date()
});

print('Arcana Cloud MongoDB initialization completed.');
