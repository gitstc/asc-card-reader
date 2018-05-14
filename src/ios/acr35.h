/********* acr35.h Cordova Plugin Header *******/

#import <Cordova/CDV.h>
#import "AudioJack.h"

@interface acr35 : CDVPlugin <ACRAudioJackReaderDelegate>

/**
 * Sets the ACR35 reader to continuously poll for the presence of a card. If a card is found,
 * the UID will be returned to the Apache Cordova application
 *
 * @param command: the command sent from Cordova
 */
- (void)read:(CDVInvokedUrlCommand*)command;

/**
 * Sends the reader to sleep after stopping the polling thread
 * 
 * @param command: the command sent from Cordova
*/
- (void)sleep:(CDVInvokedUrlCommand*)command;

/**
 * Converts raw data into a hexidecimal string
 *
 * @param buffer: raw data in the form of a byte array
 * @param length: the length of the byte array
 * @return a string containing the data in hexidecimal form
 */
- (NSString *)hexStringFromByteArray:(const uint8_t *)buffer length:(NSUInteger)length;

/**
 * Callback for Track response
 *
 * @param reader: SDK reader object
 */
- (void)reader:(ACRAudioJackReader *)reader didSendTrackData:(ACRTrackData *)trackData;

@end