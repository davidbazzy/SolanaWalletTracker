# CryptoWalletTracker

A Solana blockchain wallet monitoring application built in Java 21 that tracks crypto holdings across multiple wallets, fetches real-time market data, and displays portfolio analysis.

## Features

- Track multiple Solana wallets simultaneously
- Real-time USD price updates via Jupiter API
- Token metadata fetched from via Helius API
- PostgreSQL persistence for wallets, tokens, and positions
- Concurrent architecture using Java 21 virtual threads to for token metadata calls to Helius (frequent I/O calls)
- View holdings across all wallets and find tokens present in multiple wallets

## Architecture

### Core Components

| Component | Description                                                                              |
|-----------|------------------------------------------------------------------------------------------|
| `Processor` | Central orchestrator - manages wallets/tokens, handles user commands                     |
| `WalletService` | Fetches wallet data from Solana RPC, retrieves token metadata from Helius API (via REST) |
| `MarketDataProcessor` | Fetches USD prices from Jupiter API (via REST) on a 15-second interval                   |
| `DatabaseConnUtil` | PostgreSQL persistence layer (singleton connection)                                      |

### Data Model

```
Wallet
├── address (public key)
├── name (user-assigned label)
├── solBalance (native SOL)
└── positions (Map<String, Position>)

Token
├── mintAddress
├── name / ticker
├── decimals
└── marketData (USD price)

Position
├── walletAddress
├── accountAddress
├── token
├── tokenBalance
└── usdBalance
```

### Threading Model

The application uses a modern concurrent architecture optimized for I/O-bound operations:

```
ScheduledExecutorService (2 platform threads)
├── Market data fetching (every 15 seconds)
└── Position USD value updates (every 15 seconds)

Virtual Thread Executor (WalletService)
└── Async calls to Helius API to fetch token metadata. Using virtual threads due to large # of calls + I/O operations (performance benefits)
    └── Semaphore usage to manage Helius API rate limits (max 5 concurrent Helius API calls)
```

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                        STARTUP                              │
├─────────────────────────────────────────────────────────────┤
│  1. Load wallets & tokens from PostgreSQL                   │
│  2. For each wallet:                                        │
│     ├── Fetch SOL balance from Solana RPC                   │
│     └── Fetch token accounts → create Position objects      │
│  3. Start scheduled threads for market data & positions     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    TOKEN DISCOVERY                          │
├─────────────────────────────────────────────────────────────┤
│  1. Check in-memory cache (m_tokenMap)                      │
│  2. If missing → spawn virtual thread:                      │
│     ├── Acquire semaphore permit                            │
│     ├── Fetch metadata from Helius API                      │
│     ├── Persist to database                                 │
│     └── Release semaphore permit                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    PRICE UPDATES                            │
├─────────────────────────────────────────────────────────────┤
│  Every 15 seconds:                                          │
│  1. Batch tokens (max 99 per request)                       │
│  2. Fetch prices from Jupiter API                           │
│  3. Update MarketData objects                               │
│  4. Recalculate USD values for all positions                │
└─────────────────────────────────────────────────────────────┘
```

## External APIs

| API | Purpose | Rate Limiting |
|-----|---------|---------------|
| **Sava RPC** | Solana blockchain queries (account info, token accounts) | 5-second delays between wallet loads |
| **Helius API** | Token metadata (name, symbol, decimals) | Semaphore (5 concurrent) + 200ms delays |
| **Jupiter API** | Real-time USD prices | 4-second delays between batches |

## Commands
#### Note: Commands to be changed Java FX GUI is configured 

| Command | Description |
|---------|-------------|
| `h` | Display all wallet holdings |
| `o` | Display tokens present in multiple wallets |
| `0` | Graceful shutdown |
| `name:address` | Add or update a wallet |

## Requirements

- Java 21+
- PostgreSQL database
- API keys for Helius


## Configurations, Building & Running - TBC