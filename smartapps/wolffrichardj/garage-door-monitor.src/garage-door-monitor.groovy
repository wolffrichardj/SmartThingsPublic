/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Garage Door Monitor - Open/Close Sensor
 *
 *  Author: Richard Wolff
 */
definition(
    name: "Garage Door Monitor - Open/Close Sensor",
    namespace: "wolffrichardj",
    author: "Richard Wolff",
    description: "Monitor your garage door with open/close sensor and get a text message if it is open too long",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png"
)

preferences {
	section("When the garage door is open...") {
		input "contactsensor", "capability.contactSensor", title: "Which?"
	}
	section("For too long...") {
		input "maxOpenTime", "number", title: "Minutes?"
	}
	section("Text me at (optional, sends a push notification if not specified)...") {
        input("recipients", "contact", title: "Notify", description: "Send notifications to") {
            input "phone", "phone", title: "Phone number?", required: false
        }
	}
}

def installed()
{
	log.debug "Installed with settings: ${settings}"
	subscribe(contactsensor, "contact", contactHandler)
}

def updated()
{
	unsubscribe()
	subscribe(contactsensor, "contact", contactHandler)
}


/*************** Helpers ******************/
def clearSmsHistory() {
	state.smsHistory = null
}

def clearStatus() {
	state.status = null
}

def sendTextMessage() {
	log.debug "$contactsensor was open too long, texting $phone"

	updateSmsHistory()
	def openMinutes = maxOpenTime * (state.smsHistory?.size() ?: 1)
	def msg = "Your ${contactsensor.label ?: contactsensor.name} has been open for more than ${openMinutes} minutes!"
    if (location.contactBookEnabled) {
        sendNotificationToContacts(msg, recipients)
    }
    else {
        if (phone) {
            sendSms(phone, msg)
        } else {
            sendPush msg
        }
    }
}

def updateSmsHistory() {
	if (!state.smsHistory) state.smsHistory = []

	if(state.smsHistory.size() > 9) {
		log.debug "SmsHistory is too big, reducing size"
		state.smsHistory = state.smsHistory[-9..-1]
	}
	state.smsHistory << [sentDate: new Date().toSystemFormat()]
}

/*************** Actions ******************/
def contactHandler(evt) {
    log.debug "Event triggered with event values: ${evt.value}"
		def latestState = evt.value
		if( latestState){
			def isOpen = (evt.value == "open")
			def isNotScheduled = state.status != "scheduled"

			if (!isOpen) {
				clearSmsHistory()
				clearStatus()
			}

			if (isOpen && isNotScheduled) {
				runIn(maxOpenTime * 60, takeAction, [overwrite: false])
				state.status = "scheduled"
			}
		}
}

def takeAction(){
	if (state.status == "scheduled")
	{
		def deltaMillis = 1000 * 60 * maxOpenTime
		def timeAgo = new Date(now() - deltaMillis)

		def recentTexts = state.smsHistory.find { it.sentDate.toSystemDate() > timeAgo }

		if (!recentTexts) {
			sendTextMessage()
		}
		runIn(maxOpenTime * 60, takeAction, [overwrite: false])
	} else {
		log.trace "Status is no longer scheduled. Not sending text."
	}
}