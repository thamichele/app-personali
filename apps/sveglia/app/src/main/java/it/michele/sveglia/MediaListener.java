package it.michele.sveglia;

import android.service.notification.NotificationListenerService;

/**
 * Esiste solo per ottenere, con l'accesso alle notifiche concesso dall'utente, le sessioni multimediali attive:
 * così la sveglia può chiedere a qualsiasi app musicale di cercare e riprodurre. Le notifiche non vengono lette.
 */
public class MediaListener extends NotificationListenerService {
}
