package co.eskala.bktpj_notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pegar la cadena de conexión de Neon en el onboarding. Valores inventados, no son credenciales reales. */
class ConnectionStringTest {
    @Test fun cadenaCompletaDeNeon() {
        val c = Prefs.parseConnectionString("postgresql://usuario:clave@ep-ejemplo.us-east-2.aws.neon.tech/midb?sslmode=require")!!
        assertEquals("ep-ejemplo.us-east-2.aws.neon.tech", c.host)
        assertEquals("usuario", c.user)
        assertEquals("clave", c.password)
        assertEquals("midb", c.db)
    }

    @Test fun sinBaseUsaNeondb() {
        assertEquals("neondb", Prefs.parseConnectionString("postgres://u:p@host.neon.tech")!!.db)
        assertEquals("neondb", Prefs.parseConnectionString("postgres://u:p@host.neon.tech/?sslmode=require")!!.db)
    }

    @Test fun claveConArroba() {
        val c = Prefs.parseConnectionString("  postgresql://u:a@b@host.neon.tech/db  ")!!
        assertEquals("a@b", c.password)
        assertEquals("host.neon.tech", c.host)
    }

    @Test fun cadenasInvalidas() {
        assertNull(Prefs.parseConnectionString(""))
        assertNull(Prefs.parseConnectionString("hola"))
        assertNull(Prefs.parseConnectionString("postgresql://@host.neon.tech/db"))   // sin usuario
        assertNull(Prefs.parseConnectionString("postgresql://u:p@/db"))             // sin host
    }
}
