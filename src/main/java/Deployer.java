import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;

public class Deployer extends AbstractVerticle {
    @Override
    public void start() {
        ConfigStoreOptions store = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("raw-data", true));

        ConfigRetriever retriever = ConfigRetriever.create(vertx,
                new ConfigRetrieverOptions().addStore(store));

        retriever.getConfig(json -> {
            System.out.printf("JSON: |   %s",json.result().encodePrettily());
            vertx.deployVerticle(new WebApi(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("WebApi deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("WebApi deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new ToMQTT(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("ToMQTT deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("ToMQTT deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new Balance(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("Balance deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("Balance deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new ExecuteBalAndMini(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("ExecuteBalAndMini deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("ExecuteBalAndMini deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new Mini(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("Mini deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("Mini deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new Deposit(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("DepositCommission deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("DepositCommission deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new CardActivation(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("CardActivation deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("CardActivation deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new Withdraw(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("Withdraw deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("Withdraw deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new Funds(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("Funds deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("Funds deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new ExecuteWithdrawAndDeposit(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("ExecuteWithdrawAndDeposit deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("ExecuteWithdrawAndDeposit deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new ComAndTransactionRecords(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("ComAndTransactionRecords deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("ComAndTransactionRecords deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new PinReset(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("PinReset deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("PinReset deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new WithdrawCommission(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("WithdrawCommission deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("WithdrawCommission deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new PinChange(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("PinChange deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("PinChange deploy failed | %s\n", deploy.cause());
                }
            });
            vertx.deployVerticle(new BalTrans(), new DeploymentOptions().setConfig(json.result()), deploy -> {
                if (deploy.succeeded()) {
                    System.out.printf("BalTrans deploy succeeded | Id: %s\n", deploy.result());
                } else {
                    System.out.printf("BalTrans deploy failed | %s\n", deploy.cause());
                }
            });
        });
    }
}
