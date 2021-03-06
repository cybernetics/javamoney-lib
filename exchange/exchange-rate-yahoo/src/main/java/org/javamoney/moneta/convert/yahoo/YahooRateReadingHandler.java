/*
 * Copyright (c) 2012, 2018, Werner Keil, Anatole Tresch and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Contributors: @atsticks, @keilw, @otjava
 */
package org.javamoney.moneta.convert.yahoo;


import java.io.InputStream;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.money.convert.ConversionContextBuilder;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContext;
import javax.money.convert.RateType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.spi.DefaultNumberValue;

class YahooRateReadingHandler {

	private final Map<LocalDate, Map<String, ExchangeRate>> excangeRates;

	private final ProviderContext context;

	public YahooRateReadingHandler(final Map<LocalDate, Map<String, ExchangeRate>> excangeRates,
			final ProviderContext context) {
		this.excangeRates = excangeRates;
		this.context = context;
	}

	void parse(final InputStream stream) throws JAXBException, ParseException {
		final String yahooDtoPackage = "org.javamoney.moneta.internal.convert.yahoo";
		final Unmarshaller unmarshaller = JAXBContext.newInstance(yahooDtoPackage).createUnmarshaller();
		final YahooRoot root = (YahooRoot) unmarshaller.unmarshal(stream);
		final YahooCurrencies currencies = root.getResources();

		for (YahooQuoteItem quote : currencies.getResource()) {
			YahooQuoteItemInformation information = YahooQuoteItemInformation.of(quote.getField());

			if(!information.isCurrencyValid()) {
				continue;
			}
			addRate(information);
		}
	}

   private void addRate(YahooQuoteItemInformation information) {

        final ExchangeRateBuilder builder = new ExchangeRateBuilder(
        		ConversionContextBuilder.create(context, RateType.DEFERRED).build());
        builder.setBase(YahooAbstractRateProvider.BASE_CURRENCY);
        builder.setTerm(information.getCurrency());
        builder.setFactor(DefaultNumberValue.of(information.getValue()));
        final ExchangeRate exchangeRate = builder.build();

        Map<String, ExchangeRate> rateMap = this.excangeRates.get(information.getLocalDate());
        if (Objects.isNull(rateMap)) {
            synchronized (this.excangeRates) {
                rateMap = Optional.ofNullable(this.excangeRates.get(information.getLocalDate())).orElse(new ConcurrentHashMap<>());
                this.excangeRates.putIfAbsent(information.getLocalDate(), rateMap);
            }
        }
        rateMap.put(information.getCurrency().getCurrencyCode(), exchangeRate);

    }
}