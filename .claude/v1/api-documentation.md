# 🌐 API Documentation

**Project**: Prototype Reservation System  
**Version**: 1.0.0  
**Base URL**: `http://localhost:8080`  
**Authentication**: JWT Bearer Token  

## Table of Contents

1. [Authentication APIs](#authentication-apis)
2. [User Management APIs](#user-management-apis)
3. [Restaurant Management APIs](#restaurant-management-apis)
4. [Category APIs](#category-apis)
5. [Company APIs](#company-apis)
6. [Error Handling](#error-handling)
7. [Security Considerations](#security-considerations)

---

## Authentication APIs

### User Sign Up
**Endpoint**: `POST /api/v1/user/general/sign-up`  
**Description**: Register a new general user account  
**Authentication**: Not required  

**Request Body**:
```json
{
  "loginId": "string",
  "password": "string",
  "email": "string",
  "mobile": "string",
  "nickname": "string"
}
```

**Validation Rules**:
- `loginId`: 4-20 characters, alphanumeric only
- `password`: 8-18 characters, must contain uppercase, lowercase, number, and special character
- `email`: Valid email format
- `mobile`: Valid mobile phone format
- `nickname`: 2-10 characters

**Response**:
```json
{
  "result": true,
  "message": "User created successfully"
}
```

**Status Codes**:
- `201 Created`: User successfully created
- `400 Bad Request`: Validation errors or user already exists
- `500 Internal Server Error`: Server error

---

### User Sign In
**Endpoint**: `PUT /api/v1/user/general/sign-in`  
**Description**: Authenticate user and return access token  
**Authentication**: Not required  

**Request Body**:
```json
{
  "loginId": "string",
  "password": "string"
}
```

**Response**:
```json
{
  "accessToken": "Bearer eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

**Cookies Set**:
- `refreshToken`: HTTP-only, secure refresh token

**Status Codes**:
- `200 OK`: Authentication successful
- `401 Unauthorized`: Invalid credentials
- `423 Locked`: Account locked due to failed attempts
- `500 Internal Server Error`: Server error

---

### User Sign Out
**Endpoint**: `DELETE /api/v1/user/general/sign-out`  
**Description**: Invalidate user session and clear tokens  
**Authentication**: Required (Bearer Token)  

**Response**:
```json
{
  "result": true,
  "message": "User signed out successfully"
}
```

**Status Codes**:
- `200 OK`: Sign out successful
- `401 Unauthorized`: Invalid or expired token

---

### Token Refresh
**Endpoint**: `PUT /api/v1/user/general/refresh`  
**Description**: Refresh access token using refresh token  
**Authentication**: Refresh token in cookies  

**Response**:
```json
{
  "accessToken": "Bearer eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000
}
```

**Status Codes**:
- `200 OK`: Token refreshed successfully
- `401 Unauthorized`: Invalid or expired refresh token

---

## User Management APIs

### Get User Profile
**Endpoint**: `GET /api/v1/user/general/profile`  
**Description**: Retrieve current user's profile information  
**Authentication**: Required (Bearer Token)  

**Response**:
```json
{
  "loginId": "string",
  "email": "string",
  "mobile": "string",
  "nickname": "string",
  "role": "USER",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**Status Codes**:
- `200 OK`: Profile retrieved successfully
- `401 Unauthorized`: Invalid or expired token
- `404 Not Found`: User not found

---

### Update User Nickname
**Endpoint**: `PUT /api/v1/user/general/nickname`  
**Description**: Update user's nickname  
**Authentication**: Required (Bearer Token)  

**Request Body**:
```json
{
  "nickname": "string"
}
```

**Response**:
```json
{
  "result": true,
  "message": "Nickname updated successfully"
}
```

**Status Codes**:
- `200 OK`: Nickname updated successfully
- `400 Bad Request`: Invalid nickname format
- `401 Unauthorized`: Invalid or expired token

---

### Change Password
**Endpoint**: `PUT /api/v1/user/general/password`  
**Description**: Change user's password  
**Authentication**: Required (Bearer Token)  

**Request Body**:
```json
{
  "currentPassword": "string",
  "newPassword": "string"
}
```

**Response**:
```json
{
  "result": true,
  "message": "Password changed successfully"
}
```

**Status Codes**:
- `200 OK`: Password changed successfully
- `400 Bad Request`: Invalid password format or same as current
- `401 Unauthorized`: Invalid current password or expired token

---

### Find User IDs
**Endpoint**: `GET /api/v1/user/general/find/ids`  
**Description**: Find user login IDs by email and mobile  
**Authentication**: Not required  

**Query Parameters**:
- `email`: User's email address
- `mobile`: User's mobile phone number

**Response**:
```json
{
  "loginIds": ["loginId1", "loginId2"]
}
```

**Status Codes**:
- `200 OK`: Search completed successfully
- `400 Bad Request`: Invalid email or mobile format
- `404 Not Found`: No matching users found

---

### Reset Password
**Endpoint**: `PUT /api/v1/user/general/find/password`  
**Description**: Reset user password and send temporary password via email  
**Authentication**: Not required  

**Request Body**:
```json
{
  "loginId": "string",
  "email": "string"
}
```

**Response**:
```json
{
  "result": true,
  "message": "Temporary password sent to email"
}
```

**Status Codes**:
- `200 OK`: Temporary password sent successfully
- `400 Bad Request`: Invalid request data
- `404 Not Found`: User not found

---

## Restaurant Management APIs

### Create Restaurant
**Endpoint**: `POST /api/v1/restaurant/create`  
**Description**: Create a new restaurant  
**Authentication**: Required (Bearer Token)  
**Content-Type**: `multipart/form-data`  

**Request Parts**:
- `request` (JSON):
```json
{
  "companyId": "string",
  "name": "string",
  "introduce": "string",
  "phone": "string",
  "zipCode": "string",
  "address": "string",
  "detail": "string",
  "latitude": 37.123456,
  "longitude": 127.123456,
  "workingDays": [
    {
      "dayOfWeek": "MONDAY",
      "openTime": "09:00",
      "closeTime": "22:00"
    }
  ],
  "tags": ["tag1", "tag2"],
  "nationalities": ["KOREAN", "ITALIAN"],
  "cuisines": ["TRADITIONAL", "FUSION"]
}
```
- `photos` (MultipartFile[]): Restaurant photos (optional)

**Response**:
```json
{
  "result": true,
  "message": "Restaurant created successfully"
}
```

**Status Codes**:
- `201 Created`: Restaurant created successfully
- `400 Bad Request`: Validation errors or duplicate restaurant
- `401 Unauthorized`: Invalid or expired token

---

### Update Restaurant
**Endpoint**: `PUT /api/v1/restaurant/change`  
**Description**: Update restaurant information  
**Authentication**: Required (Bearer Token)  
**Content-Type**: `multipart/form-data`  

**Request Parts**:
- `request` (JSON): Same structure as create restaurant
- `photos` (MultipartFile[]): Updated restaurant photos (optional)

**Response**:
```json
{
  "result": true,
  "message": "Restaurant updated successfully"
}
```

**Status Codes**:
- `200 OK`: Restaurant updated successfully
- `400 Bad Request`: Validation errors
- `401 Unauthorized`: Invalid or expired token
- `403 Forbidden`: Not authorized to update this restaurant
- `404 Not Found`: Restaurant not found

---

## Category APIs

### Get Cuisines
**Endpoint**: `GET /api/v1/category/cuisines`  
**Description**: Retrieve all available cuisine categories  
**Authentication**: Not required  

**Response**:
```json
{
  "cuisines": [
    {
      "id": "string",
      "title": "Traditional Korean",
      "description": "Traditional Korean cuisine"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: Cuisines retrieved successfully

---

### Get Nationalities
**Endpoint**: `GET /api/v1/category/nationalities`  
**Description**: Retrieve all available nationality categories  
**Authentication**: Not required  

**Response**:
```json
{
  "nationalities": [
    {
      "id": "string",
      "title": "Korean",
      "description": "Korean nationality cuisine"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: Nationalities retrieved successfully

---

### Get Tags
**Endpoint**: `GET /api/v1/category/tags`  
**Description**: Retrieve all available restaurant tags  
**Authentication**: Not required  

**Response**:
```json
{
  "tags": [
    {
      "id": "string",
      "title": "Family Friendly",
      "description": "Suitable for families"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: Tags retrieved successfully

---

## Company APIs

### Get Companies
**Endpoint**: `GET /api/v1/company/self`  
**Description**: Retrieve list of companies  
**Authentication**: Required (Bearer Token)  

**Response**:
```json
{
  "companies": [
    {
      "id": "string",
      "name": "Company Name",
      "businessNumber": "123-45-67890",
      "address": "Company Address"
    }
  ]
}
```

**Status Codes**:
- `200 OK`: Companies retrieved successfully
- `401 Unauthorized`: Invalid or expired token

---

## Error Handling

### Standard Error Response Format
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Additional error details",
    "timestamp": "2024-01-01T00:00:00Z",
    "path": "/api/v1/endpoint"
  }
}
```

### Common Error Codes
- `VALIDATION_ERROR`: Input validation failed
- `AUTHENTICATION_ERROR`: Authentication required or failed
- `AUTHORIZATION_ERROR`: Insufficient permissions
- `RESOURCE_NOT_FOUND`: Requested resource not found
- `ALREADY_EXISTS`: Resource already exists
- `ACCOUNT_LOCKED`: User account is locked
- `TOKEN_EXPIRED`: JWT token has expired
- `INVALID_TOKEN`: JWT token is invalid
- `INTERNAL_SERVER_ERROR`: Unexpected server error

### HTTP Status Code Mapping
- `400 Bad Request`: Validation errors, business logic violations
- `401 Unauthorized`: Authentication failures, invalid tokens
- `403 Forbidden`: Authorization failures, insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflicts, duplicate data
- `423 Locked`: Account locked
- `429 Too Many Requests`: Rate limiting
- `500 Internal Server Error`: Unexpected server errors

---

## Security Considerations

### Authentication
- JWT tokens expire in 1 hour
- Refresh tokens are HTTP-only and secure cookies
- Bearer token format: `Authorization: Bearer <token>`

### Authorization
- Role-based access control (USER, MANAGER, ADMIN)
- Hierarchical permissions (ADMIN includes MANAGER and USER roles)

### Input Validation
- All inputs are validated for format, length, and content
- XSS protection implemented for all text inputs
- SQL injection prevention through parameterized queries

### Rate Limiting
- Consider implementing rate limiting for authentication endpoints
- Monitor for brute force attacks

### HTTPS
- Always use HTTPS in production
- Secure cookie settings for refresh tokens

### Password Security
- BCrypt hashing with salt
- Password complexity requirements enforced
- Account lockout after failed attempts

---

## Request/Response Examples

### Example: User Registration Flow
```bash
# 1. Register new user
curl -X POST http://localhost:8080/api/v1/user/general/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "loginId": "john_doe",
    "password": "SecurePass123!",
    "email": "john@example.com",
    "mobile": "010-1234-5678",
    "nickname": "John"
  }'

# 2. Sign in
curl -X PUT http://localhost:8080/api/v1/user/general/sign-in \
  -H "Content-Type: application/json" \
  -d '{
    "loginId": "john_doe",
    "password": "SecurePass123!"
  }'

# 3. Access protected resource
curl -X GET http://localhost:8080/api/v1/user/general/profile \
  -H "Authorization: Bearer <access_token>"
```

### Example: Restaurant Creation
```bash
curl -X POST http://localhost:8080/api/v1/restaurant/create \
  -H "Authorization: Bearer <access_token>" \
  -F 'request={
    "companyId": "company123",
    "name": "Great Restaurant",
    "introduce": "Amazing food and service",
    "phone": "02-123-4567",
    "zipCode": "12345",
    "address": "Seoul, Korea",
    "detail": "Near subway station",
    "latitude": 37.123456,
    "longitude": 127.123456,
    "workingDays": [
      {
        "dayOfWeek": "MONDAY",
        "openTime": "09:00",
        "closeTime": "22:00"
      }
    ],
    "tags": ["family_friendly"],
    "nationalities": ["KOREAN"],
    "cuisines": ["TRADITIONAL"]
  };type=application/json' \
  -F 'photos=@restaurant1.jpg' \
  -F 'photos=@restaurant2.jpg'
```