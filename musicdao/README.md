# Spotify 1 - Team 4

Our goal for this project was to create a more fair way for artists to be paid and reduce transaction fees from User donations. Normally, the top artists would get most of the money/donations. To address this imbalance, we introduced the concept of a Shared Wallet. Instead of donating to individual artists, users contribute to this Shared Wallet. Donations are then distributed based on a predefined "fair" algorithm, ensuring that smaller or emerging artists receive the support they need to grow.

## What have we done

### App Improvements and fixes

Detailed progress updates : https://github.com/Tribler/tribler/issues/8577

We started with app improvements and fixes such as moving statistics to overlay tab, playing songs on start up, fixing number of peers discovered.
Then we explored the ways to implement lottery function and compiled some interesting methodologies and benchmarks.
We started implementation of this by creating a shared wallet architecture first using Nsd manager and then using ipv8 community. 
We found ipv8 community to be easier to integrate with our infrastructure and so proceeded to make further enhancements.
We used dummy statistics to mimic the behaviour of 2 lottery functions - pro-rata and user-centric approaches. We have then visualised these in our app.

![Fullplayer screen on start up.jpg](images/Fullplayer%20screen%20on%20start%20up.jpg)

![Overlay tab changes.jpg](images/Overlay%20tab%20changes.jpg)

Shared Wallet

![address of shared wallet.jpg](images/address%20of%20shared%20wallet.jpg)

![Donate to shared wallet.jpg](images/Donate%20to%20shared%20wallet.jpg)

![shared wallet device receives transaction.jpg](images/shared%20wallet%20device%20receives%20transaction.jpg)

Money Distribution - Statistics and visualization

![Artist listens table.jpg](images/Artist%20listens%20table.jpg)

![Options in shared wallet.jpg](images/Options%20in%20shared%20wallet.jpg)

![visualizing prorata distribution.jpg](images/visualizing%20prorata%20distribution.jpg)

![visualizing user centric distribution.jpg](images/visualizing%20user%20centric%20distribution.jpg)

### Shared Wallet Implementation
In this section we will describe how the we implemented the Shared Wallet. 

The Shared Wallet is built by using the ipv8 TrustChain Community class. The ipv8 protocol enables the creation of a peer-to-peer network between devices. Through this network, devices can directly communicate with one another, allowing us to establish and maintain the Shared Wallet.

Currently, the concept involves designating one device (phone) to act as the Shared Wallet. We've added a button in the app that allows a device to take on this role. At any given time, only one Shared Wallet can be active within the network.

We break down the implementation in 2 important functionalities: 

1. **Broadcasting:** The broadcasting step happens when a device wants to become a Shared Wallet. On clicking the "Become Shared Wallet" button we send 2 types of ipv8 messages. Firstly, we send a **SharedWalletMessage**. This message is used to propagate the information of the device that wants to become the Shared Wallet (public key, wallet id). Secondly, we send a **SharedWalletInfoMessage**. This message is used to share the current balance of the wallet and the transactions that have been made to this wallet. Whenever there is a change in balance or a new transaction occurs, we use a SharedWalletInfoMessage to keep track of these changes. This ensures that all connected devices stay up to date with the Shared Wallet's status.

2. **Discovering:** To receive these ipv8 messages that are sent duting the broadcast phase we have set up message handlers. The high level logic in these is pretty straightforward and in these handlers we basically keep track of the information that was received from the Shared Wallet device.


### Money distribution
To ensure fairness, we use two different methods to calculate how much money each artist receives from the shared wallet:

#### Pro Rata
In the **Pro Rata** model, all revenue is pooled together and distributed among artists based on the **total number of listens** each artist receives across the platform.

- **Formula:**  
`(Listens of Artist A / Total Listens) × Total Revenue`


- **Example:**  
If Artist A is listened to 300 times, the total number of listens across the platform is 300,000, and the total revenue after fees is $600,000, then the amount Artist A receives is:

    `(300 / 300000) × 600000 = 600`

    So, Artist A receives **$600**.

#### User Centric
In the **User Centric** model, revenue is distributed based on the **listening habits of each individual user**. Each user’s payment values are split proportionally among the artists they listened to.

- **Formula:**  
`(Listens of Artist A by User 1 / Total Listens by User 1) × Revenue from User 1`


- **Example:**  
If User 1 has listened to a total of 400 tracks, and 80 of those were for Artist A, and the revenue from this user is $35, then the amount Artist A receives from this user is:

    `(80 / 400) × 35 = 7`

    So, Artist A receives **$7** from this user.


## Future Work
Below we list some ideas for future improvements to our project:

- **Having an always online Shared Wallet.** Implementing an always-online Shared Wallet. Currently, the system depends on a device being online and manually selecting the "Become Shared Wallet" option. In the future, it would be ideal to have a dedicated device that is always available to fulfill this role continuously.

- **Optimize transaction fees.** Right now we use some estimations to account for the transaction fees. By having the Shared Wallet instead of multiple individual donations one could take a look at optimizing transaction fees, so as much money as possible goes to the artists.

- **More optimized message flow.** While we did take some optimizations into account and we did some basic benchmarking it could be interesting to dive deeper in the message flow and set it up in such a way that millions of devices could communicate in a swift and efficient way.

## What was good/went well?
In this section we will describe some things that went well during the development of the project. 

- The project allowed for a clear split of tasks for good efficiency in the team. 
- The teamwork was great and we were able to coordinate well and help each other out if needed.

## What was not clear?
In this section we will describe some struggles we had during the project.

- The codebase was quite large and overwhelming at times making it harder to get organized in the starting phase of the project.
- We depended on external services to fund test wallets (BTC faucet) and show transactions on the receiver’s device. When these services went down at the last minute, we tried set up a local server based on `Tribler/bitcoin-regtest-node` server and use its `/addBTC` endpoint to add funds. While the server worked, linking it into the app took extra effort due to multiple changes in code and ultimately we were unsuccessful in the alternate server set up.