package function.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

import java.util.function.Function;

@SpringBootApplication
public class RepeaterApplication {

	@Bean
	public Function<Tuple2<Flux<CartEvent>, Flux<CheckoutEvent>>, Flux<OrderEvent>> fn() {
		return tuple -> {
			Flux<CartEvent> cartEventStream = tuple.getT1();
			Flux<CheckoutEvent> checkoutEventStream = tuple.getT2();

			return Flux.zip(cartEventStream, checkoutEventStream, (cartEvent, checkoutEvent) -> {
				OrderEvent oe = new OrderEvent();
				oe.setOrderEvent(cartEvent.toString() + "- " + checkoutEvent.toString());
				return oe;
			});
		};
	}

    public static void main(String[] args) {
        SpringApplication.run(RepeaterApplication.class, args);
    }

    public static class CartEvent {
		private String carEvent;

		public String getCarEvent() {
			return carEvent;
		}

		public void setCarEvent(String carEvent) {
			this.carEvent = carEvent;
		}

		public String toString() {
			return "CartEvent: " + carEvent;
		}
	}

	public static class CheckoutEvent {
		private String checkoutEvent;

		public String getCheckoutEvent() {
			return checkoutEvent;
		}

		public void setCheckoutEvent(String checkoutEvent) {
			this.checkoutEvent = checkoutEvent;
		}

		public String toString() {
			return "CheckoutEvent: " + checkoutEvent;
		}
	}

	public static class OrderEvent {
		private String orderEvent;

		public String getOrderEvent() {
			return orderEvent;
		}

		public void setOrderEvent(String orderEvent) {
			this.orderEvent = orderEvent;
		}

		public String toString() {
			return "OrderEvent: " + orderEvent;
		}
	}

}
