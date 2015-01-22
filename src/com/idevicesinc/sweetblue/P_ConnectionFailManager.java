package com.idevicesinc.sweetblue;

import static com.idevicesinc.sweetblue.BleDeviceState.ATTEMPTING_RECONNECT;

import com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener;
import com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.Please;
import com.idevicesinc.sweetblue.PA_StateTracker.E_Intent;
import com.idevicesinc.sweetblue.utils.Interval;

/**
 * 
 * 
 *
 */
class P_ConnectionFailManager
{
	private final BleDevice m_device;
	private final P_ReconnectManager m_reconnectMngr;
	private final P_Logger m_logger;
	
	private ConnectionFailListener m_connectionFailListener = BleDevice.DEFAULT_CONNECTION_FAIL_LISTENER;
	
	private int m_failCount = 0;
	
	P_ConnectionFailManager(BleDevice device, P_ReconnectManager reconnectMngr)
	{
		m_device = device;
		m_reconnectMngr = reconnectMngr;
		m_logger = m_device.getManager().getLogger();
	}
	
	void onExplicitDisconnect()
	{
		resetFailCount();
	}
	
	void onFullyInitialized()
	{
		resetFailCount();
	}
	
	void onExplicitConnectionStarted()
	{
		resetFailCount();
	}
	
	private void resetFailCount()
	{
		m_failCount = 0;
	}
	
	int getRetryCount()
	{
		int retryCount = m_failCount;
		
		return retryCount;
	}
	
	Please onConnectionFailed(ConnectionFailListener.Reason reason_nullable, boolean isAttemptingReconnect)
	{
		if( reason_nullable == null )  return Please.DO_NOT_RETRY;
		
		m_logger.w(reason_nullable+"");
		
		if( isAttemptingReconnect )
		{
			m_failCount = 1;
		}
		else
		{
			m_failCount++;
		}
		
		Please retryChoice = null;
		Interval attemptTime = Interval.ZERO; //TODO
		
		if( m_connectionFailListener != null )
		{
			retryChoice = m_connectionFailListener.onConnectionFail(m_device, reason_nullable, m_failCount, attemptTime, attemptTime);
		}
		else if( m_device.getManager().m_defaultConnectionFailListener != null )
		{
			retryChoice = m_device.getManager().m_defaultConnectionFailListener.onConnectionFail(m_device, reason_nullable, m_failCount, attemptTime, attemptTime);
		}
		
		retryChoice = retryChoice != null ? retryChoice : Please.DO_NOT_RETRY;
		retryChoice = !isAttemptingReconnect ? retryChoice : Please.DO_NOT_RETRY;
		
		if( reason_nullable != null && reason_nullable.wasCancelled() )
		{
			retryChoice = Please.DO_NOT_RETRY;
		}
		else
		{
			if( !m_reconnectMngr.onConnectionFailed() )
			{
				//--- DRK > State change may be redundant.
				m_device.getStateTracker().update(E_Intent.IMPLICIT, ATTEMPTING_RECONNECT, false);
			}
			
			m_device.getManager().onConnectionFailed();
		}
		
		if( retryChoice == Please.RETRY )
		{
			m_device.attemptReconnect();
		}
		else
		{
			m_failCount = 0;
		}
		
		return retryChoice;
	}
	
	public void setListener(ConnectionFailListener listener)
	{
		synchronized (m_device.m_threadLock)
		{
			if( listener != null )
			{
				m_connectionFailListener = new P_WrappingDeviceStateListener(listener, m_device.getManager().m_mainThreadHandler, m_device.getManager().m_config.postCallbacksToMainThread);
			}
			else
			{
				m_connectionFailListener = null;
			}
		}
	}
}
