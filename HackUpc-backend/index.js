const express = require('express');
const path = require('path');
const multer = require('multer'); // Middleware for handling file uploads

const app = express();
const PORT = 8080;
const AWS = require('aws-sdk');
const fs = require('fs');

// Set up AWS credentials
AWS.config.update({
accessKeyId: '-',
secretAccessKey: '-',
region: 'eu-central-1'
});

// Set up Multer to handle file uploads
const storage = multer.diskStorage({
destination: function (req, file, cb) {
cb(null, 'uploads/'); // Save uploaded files to the "uploads" directory
},
filename: function (req, file, cb) {
cb(null, Date.now() + path.extname(file.originalname)); // Rename files to avoid collisions
}
});

const upload = multer({ storage: storage });

// Define a route to serve the HTML page
app.get('/', (req, res) => {
res.sendFile(path.join(__dirname, 'index.html'));
});

// Create a Textract client
const textract = new AWS.Textract();
const polly = new AWS.Polly();
const axios = require('axios');


// Call AWS Textract to extract text
app.post('/upload', upload.single('image'), (req, res) => {
if (!req.file) {
return res.status(400).send('No file uploaded.');
}

// Read the image file
const imageFile = fs.readFileSync(req.file.path);
const params = {
Document: {
Bytes: imageFile
}
};

// Call AWS Textract to extract text
textract.detectDocumentText(params, function(err, data) {
if (err) {
console.error('Error extracting text:', err);
res.status(500).send('Error extracting text from the image.');
} else {
// Extracted text from AWS Textract
const extractedText = data.Blocks.map(block => block.Text).join(' ');
console.log(extractedText);
// Define regular expressions to match patterns for boarding time, departure gate, and seat
const boardingTimeRegex = /\b\d{1,2}:\d{2}\b/g; // Matches HH:MM format
const departureGateRegex = /\b[A-Z]\d{2}\b/g; // Matches LETTER followed by two NUMBERS format
const seatRegex = /\b\d{2}[A-Z]\b/g; // Matches two NUMBERS followed by a LETTER format
const flightNumberRegex = /\b[A-Z]{2}\d{4}\b/g;
// Find matches in the extracted text
const boardingTimes = extractedText.match(boardingTimeRegex);
const departureGates = extractedText.match(departureGateRegex);
const seats = extractedText.match(seatRegex);
const flightNumbers = extractedText.match(flightNumberRegex);

// Choose the earliest boarding time
const boardingTime = boardingTimes ? boardingTimes.reduce((earliest, current) => {
const earliestTime = new Date(`01/01/2022 ${earliest}`);
const currentTime = new Date(`01/01/2022 ${current}`);
return earliestTime < currentTime ? earliest : current;
}) : "N/A";

// Choose the departure gate with the specified format
const departureGate = departureGates ? departureGates.find(gate => /^[A-Z]\d{2}$/.test(gate)) : "N/A";

// Choose the seat with the specified format
const seat = seats ? seats.find(seat => /^\d{2}[A-Z]$/.test(seat)) : "N/A";

const textToSpeak = `Hi. Your Boarding pass has been scanned sucesfully. Your boarding time is ${boardingTime}, your departure gate is ${departureGate}, and your seat is ${seat}.`; // Create the text to be spoken
generateAudio(textToSpeak, (err, audioFilePath) => {
if (err) {
console.error('Error generating audio file:', err);
// Handle error appropriately
res.status(500).send('Error generating audio file.');
} else {
// Send the audio file as response
const absolutePath = path.resolve(audioFilePath);
res.sendFile(absolutePath);
}
});
}
});
});

const generateAudio = (text, callback) => {
const params = {
OutputFormat: 'mp3',
Text: text,
VoiceId: 'Joanna' // Reemplaza 'tu-voice-id' con el ID de la voz que desees
};

polly.synthesizeSpeech(params, (err, data) => {
if (err) {
console.error('Error generating audio:', err);
callback(err);
} else {
// Save audio file
fs.writeFile('./audios/audio.mp3', data.AudioStream, 'binary', (err) => {
if (err) {
console.error('Error saving audio file:', err);
callback(err);
} else {
console.log('Audio file saved successfully');
callback(null, './audios/audio.mp3');
}
});
}
});
};


const moment = require('moment'); 
app.get('/flight-info-speech/:flightCode', async (req, res) => {
try {
const { flightCode } = req.params;
const accessKey = '-';

const flightInfo = await axios.get('http://api.aviationstack.com/v1/flights', {
params: {
access_key: accessKey,
flight_iata: flightCode // Use flight_iata parameter for filtering by flight number
}
});

if (flightInfo.data.data.length === 0) {
return res.status(404).json({ error: 'Flight information not found for the requested flight code' });
}

// Assuming you want to return information about the latest flight with the given code
const latestFlight = flightInfo.data.data[0];

// Convert scheduled departure and arrival times to a readable format
const formattedDepartureTime = moment(latestFlight.departure.scheduled).format('LLLL');
const formattedArrivalTime = moment(latestFlight.arrival.scheduled).format('LLLL');

// Construct a sentence with the flight information including luggage
const flightMessage = `Flight ${latestFlight.flight.iata} is currently ${latestFlight.flight_status}. Departure is scheduled at ${formattedDepartureTime}, gate ${latestFlight.departure.gate}. Arrival is scheduled at ${formattedArrivalTime}, gate ${latestFlight.arrival.gate}. Baggage information: ${latestFlight.arrival.baggage}.`;

// Generate audio from the flight message
generateAudio(flightMessage, (err, audioFilePath) => {
if (err) {
console.error('Error generating audio file:', err);
// Handle error appropriately
res.status(500).send('Error generating audio file.');
} else {
// Send the audio file as response
const absolutePath = path.resolve(audioFilePath);
res.sendFile(absolutePath);
}
});
} catch (error) {
console.error('Error fetching flight information:', error);
res.status(500).json({ error: 'Error fetching flight information' });
}
});

app.get('/flight-details/:flightCode', async (req, res) => {
try {
const { flightCode } = req.params;
const accessKey = '-';

const flightInfo = await axios.get('http://api.aviationstack.com/v1/flights', {
params: {
access_key: accessKey,
flight_iata: flightCode // Use flight_iata parameter for filtering by flight number
}
});

if (flightInfo.data.data.length === 0) {
return res.status(404).json({ error: 'Flight information not found for the requested flight code' });
}

// Assuming you want to return information about the latest flight with the given code
const latestFlight = flightInfo.data.data[0];

// Convert scheduled departure time to a readable format
const formattedDepartureTime = moment(latestFlight.departure.scheduled).format('LLLL');

// Construct an object with the flight information
const flightDetails = {
flightStatus: latestFlight.flight_status,
departureTime: formattedDepartureTime,
departureGate: latestFlight.departure.gate
};
console.log(flightDetails);
res.json(flightDetails);
} catch (error) {
console.error('Error fetching flight information:', error);
res.status(500).json({ error: 'Error fetching flight information' });
}
});

app.get('/random-flights', async (req, res) => {
try {
const accessKey = '-';

const flightInfo = await axios.get('http://api.aviationstack.com/v1/flights', {
params: {
access_key: accessKey,
limit: 100 // Limiting to 100 random flights
}
});

if (flightInfo.data.data.length === 0) {
return res.status(404).json({ error: 'No flight information found' });
}

const randomFlights = flightInfo.data.data;

res.json(randomFlights);
} catch (error) {
console.error('Error fetching random flights:', error);
res.status(500).json({ error: 'Error fetching random flights' });
}
});

app.post('/extract-flight-number', upload.single('image'), (req, res) => {
if (!req.file) {
return res.status(400).send('No file uploaded.');
}

// Lee el archivo de imagen
const imageFile = fs.readFileSync(req.file.path);
const params = {
Document: {
Bytes: imageFile
}
};

// Llama a AWS Textract para extraer el texto
textract.detectDocumentText(params, function(err, data) {
if (err) {
console.error('Error extracting text:', err);
res.status(500).send('Error extracting text from the image.');
} else {
// Extrae el texto del bloque de Textract
const extractedText = data.Blocks.map(block => block.Text).join(' ');

// Define una expresión regular para encontrar el número de vuelo
const flightNumberRegex = /[A-Z]{2}\s*\d{4}/g;

// Encuentra coincidencias en el texto extraído
const flightNumbers = extractedText.match(flightNumberRegex);

// Verifica si se encontró un número de vuelo
if (flightNumbers && flightNumbers.length > 0) {
// Devuelve el primer número de vuelo encontrado
const flightNumber = flightNumbers[0].replace(/\s/g, ''); // Elimina espacios en blanco
console.log(flightNumber)
res.json({ flightNumber: flightNumber });
} else {
res.status(404).send('Flight number not found in the image.');
}
}
});
});
// Start the server
app.listen(PORT, () => {
console.log(`App running on http://localhost:${PORT}`);
});

