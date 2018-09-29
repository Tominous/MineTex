package net.dv8tion.jda.core.utils.data;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

public class ArraySerializeTest
{
    @Test
    public void testSerialization()
    {
        DataArray arr = new DataArray()
            .put(1)
            .put("1")
            .put(2L)
            .put("2")
            .put(0.5D)
            .put("0.5")
            .put("String")
            .put(true)
            .put("false")
            .put(new BigInteger("50"))
            .put("50")
            .put(new DataObject().put("test", "yey"))
            .put(new DataArray().put("f"))
            .put(null);
        String expected = "[" +
            "1," +
            "\"1\"," +
            "2," +
            "\"2\"," +
            "0.5," +
            "\"0.5\"," +
            "\"String\"," +
            "true," +
            "\"false\"," +
            "50," +
            "\"50\"," +
            "{\"test\":\"yey\"}," +
            "[\"f\"]," +
            "null" +
            "]";
        Assert.assertEquals(expected, arr.toString());
    }
}