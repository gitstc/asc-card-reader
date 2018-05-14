/********* acr35.m Cordova Plugin Implementation *******/

#import "acr35.h"
#import <CommonCrypto/CommonCrypto.h>
#import "AJDHex.h"

/**
 * This class allows control of the ACR35 reader sleep state and PICC commands
 */
@implementation acr35

/** AudioJackReader object */
ACRAudioJackReader *_reader;
/** The ID corresponding to the command sent by Apache Cordova */
NSString *myCallbackId;
 
/** Is this plugin being initialised? */
bool firstRun = true;
/** Is this the first reset of the reader? */
bool firstReset = true;
 
/** APDU command for reading a card's UID */
uint8_t commandApdu[] = { 0xFF, 0xCA, 0x00, 0x00, 0x00 };
/** the integer representing card type */
NSUInteger cardType;
/** Timeout for APDU response (in <b>seconds</b>) */
NSUInteger timeout = 1;
 
/** Stop the polling thread? */
bool killThread = false;
/** Is the reader currently connected? */
bool readerConnected = true;
/** The number of iterations that have passed with no response from the reader */
int itersWithoutResponse = 0;

- (NSString *)hexStringFromByteArray:(const uint8_t *)buffer length:(NSUInteger)length {
    
    NSString *hexString = @"";
    NSUInteger i = 0;
    
    for (i = 0; i < length; i++) {
        if (i == 0) {
            hexString = [hexString stringByAppendingFormat:@"%02X", buffer[i]];
        } else {
            hexString = [hexString stringByAppendingFormat:@" %02X", buffer[i]];
        }
    }
    
    return hexString;
}

- (void)sleep:(CDVInvokedUrlCommand*)command {
	/* Kill the polling thread */
	killThread = true;
    
    /* Set the reader asleep */
	[_reader sleep];

	/* Send a success message back to Cordova */
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"asleep"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)read:(CDVInvokedUrlCommand*)command {

	/* Class variables require initialisation on first launch */
	if(firstRun){
	    // Initialize ACRAudioJackReader object.
	    _reader = [[ACRAudioJackReader alloc] init];
	    [_reader setDelegate:self];
	    firstRun = false;
	}
	firstReset = true;

	/* Set the card type */
	cardType = [[command.arguments objectAtIndex:0] intValue];

	/* Get the callback ID of the current command */
    myCallbackId = command.callbackId;
	
    /* Reset the reader */
    [_reader reset];
}

#pragma mark - Audio Jack Reader

- (void)reader:(ACRAudioJackReader *)reader didSendTrackData:(ACRTrackData *)trackData {
    CDVPluginResult* pluginResult = nil;

    ACRTrack1Data *track1Data = [[ACRTrack1Data alloc] init];
    ACRTrack2Data *track2Data = [[ACRTrack2Data alloc] init];
    ACRTrack1Data *track1MaskedData = [[ACRTrack1Data alloc] init];
    ACRTrack2Data *track2MaskedData = [[ACRTrack2Data alloc] init];
    NSString *track1MacString = @"";
    NSString *track2MacString = @"";

    NSUserDefaults *_defaults = [NSUserDefaults standardUserDefaults];
    NSData *_aesKey = [_defaults dataForKey:@"AesKey"];
    if (_aesKey == nil) {
        _aesKey = [AJDHex byteArrayFromHexString:@"4E 61 74 68 61 6E 2E 4C 69 20 54 65 64 64 79 20"];
    }

    // TODO: Add code here to process the track data.
    /*if ((trackData.track1ErrorCode != ACRTrackErrorSuccess) ||
        (trackData.track2ErrorCode != ACRTrackErrorSuccess)) {
        
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid card data"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:myCallbackId];

        return;
    }*/

    if ([trackData isKindOfClass:[ACRAesTrackData class]]) {
        ACRAesTrackData *aesTrackData = (ACRAesTrackData *) trackData;
        uint8_t *buffer = (uint8_t *) [aesTrackData.trackData bytes];
        NSUInteger bufferLength = [aesTrackData.trackData length];
        uint8_t decryptedTrackData[128];
        size_t decryptedTrackDataLength = 0;

        // Decrypt the track data.
        if (![self decryptData:buffer dataInLength:bufferLength key:[_aesKey bytes] keyLength:[_aesKey length] dataOut:decryptedTrackData dataOutLength:sizeof(decryptedTrackData) pBytesReturned:&decryptedTrackDataLength]) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Could not decrypt data"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:myCallbackId];
            return;
        }

        // Verify the track data.
        /*if (![_reader verifyData:decryptedTrackData length:decryptedTrackDataLength]) {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Could not verify data"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:myCallbackId];
            return;
        }*/

        // Decode the track data.
        track1Data = [track1Data initWithBytes:decryptedTrackData length:trackData.track1Length];
        track2Data = [track2Data initWithBytes:decryptedTrackData + 79 length:trackData.track2Length];

        NSMutableDictionary *cardData = [[NSMutableDictionary alloc] init];
        [cardData setObject:track1Data.track1String forKey:@"Track1"];
        [cardData setObject:track2Data.track2String forKey:@"Track2"];
        
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:cardData];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:myCallbackId];
    }
}

- (BOOL)decryptData:(const void *)dataIn dataInLength:(size_t)dataInLength key:(const void *)key keyLength:(size_t)keyLength dataOut:(void *)dataOut dataOutLength:(size_t)dataOutLength pBytesReturned:(size_t *)pBytesReturned {

    BOOL ret = NO;

    // Decrypt the data.
    if (CCCrypt(kCCDecrypt, kCCAlgorithmAES128, 0, key, keyLength, NULL, dataIn, dataInLength, dataOut, dataOutLength, pBytesReturned) == kCCSuccess) {
        ret = YES;
    }

    return ret;
}

@end