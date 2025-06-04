High level notes with regards to basic functionality for the wallet tracker. Key objectives:

1) Request wallet address
    1.1) Request name/label for a given wallet
2) Fetch all tokens present in wallet
3) Thread to fetch market data for each of the tokens available
3) Display SOL and USD value for each token
4) Categorize each of the tokens into groups (I'll create the labels - animal memes, AI etc)
5) Thread to track wallet holdings/contents (i.e - if balance for WIF has decreased, this should be reflected)
    -> Or can make this an asynchronous call (refresh button) to update wallet holdings
        -> UPDATE:
            - Done via 'o' command for now.
            - Need to handle rate limit API calls for jupiter as loads of token info ends up missing
            - Need to filter out spam coins as these are dangerous (Add additional filter to filter by liquidity for a given market?
            - Can also add details to show time since first buy (differentiate between old & newly held coins)
6) Display the wallet holdings in a table format
6) upload to github
7) Could use SolanaFM for fetching token data instead for given wallet? Compare rate limits to sava eng's limit

// Web app
1) Create a simple web app that displays the wallet data & balances
2) To use an AI webbuilder & export the code to a web app or to start from scratch using cursor to suggest initial code structure?


// Additional features (long term)
1) Data Storage - Could eventually use Redis to store in memory data for caching
1) View common tokens across wallets
2) Source market sentiment information for token and expose it in the app (Especially for the tokens that are common across wallets)
3) Show trade history for a given token (across provided wallets)



// Motivation/Thoughts:
1) Create wallet tracker which can provide a hollistic view of tokens held by wallets provided
2) Can provide insight on overlapping tokens across wallets (Gives an idea of popular SOL tokens at a given point in time)
    -> Including time since first buy