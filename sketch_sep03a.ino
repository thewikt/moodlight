#include <SoftwareSerial.h>

int redPin = 11;
int greenPin = 10;
int bluePin = 9;
int sensorPin = 0;
int prev = 0;
int now = 0;
int change = 0;
int baseR = 0;
int baseG = 0;
int baseB = 0;
int nextR = 0;
int nextG = 0;
int nextB = 0;
float brightness = 0;
SoftwareSerial BT(12, 13);
 
void setup()
{
  pinMode(redPin, OUTPUT);
  pinMode(greenPin, OUTPUT);
  pinMode(bluePin, OUTPUT);  
  baseR = 255;
  baseG = 255;
  baseB = 255;
  BT.begin(9600);
}
 
void loop()
{
  if (BT.available() > 0){
    baseR = BT.parseInt();
    baseG = BT.parseInt();
    baseB = BT.parseInt();
    BT.println("Set");
  }
  prev = now;
  now = analogRead(sensorPin);
  change = min(abs(now - prev)*2, 1023);
  brightness = (float) map(now, 0, 1023, 0, 255);
  brightness = max(brightness - (float) change, 0);
  brightness = brightness / 255;
  nextR = (int) baseR*brightness;
  nextG = (int) baseG*brightness;
  nextB = (int) baseB*brightness;
  setColor(nextR, nextG, nextB);
}
 
void setColor(int red, int green, int blue)
{
  analogWrite(redPin, red);
  analogWrite(greenPin, green);
  analogWrite(bluePin, blue);  
}
